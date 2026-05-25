package com.imageencrypter.subscriber;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MpiLauncher {

    private static final String MPI_BINARY = "/app/encrypt";

    public static void run(String inputPath, String outputPath,
                           String operation, String mode, String key)
            throws Exception {

        ProcessBuilder pb = new ProcessBuilder(
                "mpirun",
                "--allow-run-as-root",
                "--host", "subscriber:2,mpi-worker:2",
                MPI_BINARY,
                inputPath, outputPath, operation, mode, key
        );
        pb.redirectErrorStream(true);

        System.out.println("Launching MPI: " + String.join(" ", pb.command()));

        Process process = pb.start();

        // Read output
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[MPI] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("MPI process exited with code " + exitCode);
        }
    }
}
