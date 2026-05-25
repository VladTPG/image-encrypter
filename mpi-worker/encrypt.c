#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <mpi.h>
#include <omp.h>
#include <openssl/evp.h>
#include <openssl/rand.h>

#define AES_BLOCK_SIZE 16
#define AES_KEY_SIZE 32

static int hex_to_bytes(const char *hex, unsigned char *out, int out_len) {
    for (int i = 0; i < out_len; i++) {
        unsigned int byte;
        if (sscanf(hex + 2 * i, "%2x", &byte) != 1) return -1;
        out[i] = (unsigned char)byte;
    }
    return 0;
}

/* Derive a per-rank IV from the key and rank index (for CBC mode) */
static void derive_iv(const unsigned char *key, int rank, unsigned char *iv) {
    unsigned char input[AES_BLOCK_SIZE];
    memset(input, 0, AES_BLOCK_SIZE);
    memcpy(input, &rank, sizeof(int));

    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    int outlen;
    EVP_EncryptInit_ex(ctx, EVP_aes_256_ecb(), NULL, key, NULL);
    EVP_CIPHER_CTX_set_padding(ctx, 0);
    EVP_EncryptUpdate(ctx, iv, &outlen, input, AES_BLOCK_SIZE);
    EVP_EncryptFinal_ex(ctx, iv + outlen, &outlen);
    EVP_CIPHER_CTX_free(ctx);
}

/*
 * Process a chunk with AES-256 ECB.
 * In ECB mode each 16-byte block is independent, so we use OpenMP.
 */
static int process_ecb(const unsigned char *in, unsigned char *out, int len,
                       const unsigned char *key, int encrypt) {
    int num_blocks = len / AES_BLOCK_SIZE;

    #pragma omp parallel for schedule(static)
    for (int i = 0; i < num_blocks; i++) {
        EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
        int outlen;
        if (encrypt) {
            EVP_EncryptInit_ex(ctx, EVP_aes_256_ecb(), NULL, key, NULL);
        } else {
            EVP_DecryptInit_ex(ctx, EVP_aes_256_ecb(), NULL, key, NULL);
        }
        EVP_CIPHER_CTX_set_padding(ctx, 0);
        if (encrypt) {
            EVP_EncryptUpdate(ctx, out + i * AES_BLOCK_SIZE, &outlen,
                              in + i * AES_BLOCK_SIZE, AES_BLOCK_SIZE);
        } else {
            EVP_DecryptUpdate(ctx, out + i * AES_BLOCK_SIZE, &outlen,
                              in + i * AES_BLOCK_SIZE, AES_BLOCK_SIZE);
        }
        EVP_CIPHER_CTX_free(ctx);
    }
    return 0;
}

/*
 * Process a chunk with AES-256 CBC.
 * CBC is sequential within a chunk, but each MPI rank has its own chain.
 */
static int process_cbc(const unsigned char *in, unsigned char *out, int len,
                       const unsigned char *key, const unsigned char *iv,
                       int encrypt) {
    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    int outlen, final_len;

    if (encrypt) {
        EVP_EncryptInit_ex(ctx, EVP_aes_256_cbc(), NULL, key, iv);
    } else {
        EVP_DecryptInit_ex(ctx, EVP_aes_256_cbc(), NULL, key, iv);
    }
    EVP_CIPHER_CTX_set_padding(ctx, 0);

    if (encrypt) {
        EVP_EncryptUpdate(ctx, out, &outlen, in, len);
        EVP_EncryptFinal_ex(ctx, out + outlen, &final_len);
    } else {
        EVP_DecryptUpdate(ctx, out, &outlen, in, len);
        EVP_DecryptFinal_ex(ctx, out + outlen, &final_len);
    }

    EVP_CIPHER_CTX_free(ctx);
    return 0;
}

int main(int argc, char *argv[]) {
    MPI_Init(&argc, &argv);

    int rank, size;
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &size);

    if (argc != 6) {
        if (rank == 0)
            fprintf(stderr, "Usage: %s <input.bmp> <output.bmp> <encrypt|decrypt> <ecb|cbc> <64-hex-key>\n", argv[0]);
        MPI_Finalize();
        return 1;
    }

    const char *input_path = argv[1];
    const char *output_path = argv[2];
    int encrypt = (strcmp(argv[3], "encrypt") == 0);
    int use_cbc = (strcmp(argv[4], "cbc") == 0);
    const char *hex_key = argv[5];

    /* Parse the AES key */
    unsigned char key[AES_KEY_SIZE];
    if (strlen(hex_key) != 64 || hex_to_bytes(hex_key, key, AES_KEY_SIZE) != 0) {
        if (rank == 0)
            fprintf(stderr, "Error: key must be 64 hex characters (256-bit)\n");
        MPI_Finalize();
        return 1;
    }

    unsigned char *header = NULL;
    unsigned char *pixel_data = NULL;
    uint32_t header_size = 0;
    uint32_t pixel_size = 0;
    uint32_t original_pixel_size = 0;
    uint32_t padded_pixel_size = 0;

    /* Rank 0 reads the BMP file */
    if (rank == 0) {
        FILE *fp = fopen(input_path, "rb");
        if (!fp) {
            fprintf(stderr, "Error: cannot open %s\n", input_path);
            MPI_Abort(MPI_COMM_WORLD, 1);
            return 1;
        }

        /* Read BMP signature */
        unsigned char sig[2];
        fread(sig, 1, 2, fp);
        if (sig[0] != 'B' || sig[1] != 'M') {
            fprintf(stderr, "Error: not a BMP file\n");
            fclose(fp);
            MPI_Abort(MPI_COMM_WORLD, 1);
            return 1;
        }

        /* Get file size from header (bytes 2-5) */
        uint32_t file_size;
        fread(&file_size, 4, 1, fp);

        /* Skip reserved (bytes 6-9) */
        fseek(fp, 10, SEEK_SET);

        /* Get pixel data offset (bytes 10-13) */
        fread(&header_size, 4, 1, fp);

        /* Read the full header */
        header = (unsigned char *)malloc(header_size);
        fseek(fp, 0, SEEK_SET);
        fread(header, 1, header_size, fp);

        /* Read pixel data */
        fseek(fp, 0, SEEK_END);
        long total_size = ftell(fp);
        original_pixel_size = total_size - header_size;
        fseek(fp, header_size, SEEK_SET);

        /* Pad pixel data to be divisible by (AES_BLOCK_SIZE * size) */
        uint32_t block_unit = AES_BLOCK_SIZE * size;
        padded_pixel_size = original_pixel_size;
        if (padded_pixel_size % block_unit != 0) {
            padded_pixel_size += block_unit - (padded_pixel_size % block_unit);
        }

        pixel_data = (unsigned char *)calloc(padded_pixel_size, 1);
        fread(pixel_data, 1, original_pixel_size, fp);
        fclose(fp);

        pixel_size = padded_pixel_size;

        printf("[Rank 0] BMP header: %u bytes, pixel data: %u bytes (padded to %u), using %d MPI ranks\n",
               header_size, original_pixel_size, padded_pixel_size, size);
    }

    /* Broadcast metadata */
    MPI_Bcast(&header_size, 1, MPI_UINT32_T, 0, MPI_COMM_WORLD);
    MPI_Bcast(&pixel_size, 1, MPI_UINT32_T, 0, MPI_COMM_WORLD);
    MPI_Bcast(&original_pixel_size, 1, MPI_UINT32_T, 0, MPI_COMM_WORLD);

    /* Each rank gets an equal chunk */
    uint32_t chunk_size = pixel_size / size;
    unsigned char *local_in = (unsigned char *)malloc(chunk_size);
    unsigned char *local_out = (unsigned char *)malloc(chunk_size);

    /* Scatter pixel data */
    MPI_Scatter(pixel_data, chunk_size, MPI_UNSIGNED_CHAR,
                local_in, chunk_size, MPI_UNSIGNED_CHAR,
                0, MPI_COMM_WORLD);

    /* Process the chunk */
    if (use_cbc) {
        unsigned char iv[AES_BLOCK_SIZE];
        derive_iv(key, rank, iv);
        process_cbc(local_in, local_out, chunk_size, key, iv, encrypt);
    } else {
        process_ecb(local_in, local_out, chunk_size, key, encrypt);
    }

    printf("[Rank %d] Processed %u bytes\n", rank, chunk_size);

    /* Gather results */
    unsigned char *result = NULL;
    if (rank == 0) {
        result = (unsigned char *)malloc(pixel_size);
    }
    MPI_Gather(local_out, chunk_size, MPI_UNSIGNED_CHAR,
               result, chunk_size, MPI_UNSIGNED_CHAR,
               0, MPI_COMM_WORLD);

    /* Rank 0 writes the output file */
    if (rank == 0) {
        FILE *fp = fopen(output_path, "wb");
        if (!fp) {
            fprintf(stderr, "Error: cannot open %s for writing\n", output_path);
            MPI_Abort(MPI_COMM_WORLD, 1);
            return 1;
        }

        /* Write original header unchanged */
        fwrite(header, 1, header_size, fp);
        /* Write only the original (unpadded) pixel data */
        fwrite(result, 1, original_pixel_size, fp);
        fclose(fp);

        printf("[Rank 0] Output written to %s\n", output_path);

        free(header);
        free(pixel_data);
        free(result);
    }

    free(local_in);
    free(local_out);

    MPI_Finalize();
    return 0;
}
