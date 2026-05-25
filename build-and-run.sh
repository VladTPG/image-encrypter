#!/bin/bash
# ISM HTC/HPC Security - Image Encrypter
# Build and run all Docker containers

set -e

echo "=== Image Encrypter - Build & Run ==="
echo ""

# Generate SSH keys for MPI communication if they don't exist
if [ ! -f keys/id_rsa ]; then
    echo "[1/3] Generating SSH keys for MPI cross-container communication..."
    mkdir -p keys
    ssh-keygen -t rsa -f keys/id_rsa -N "" -q
    mkdir -p subscriber/keys mpi-worker/keys
    cp keys/id_rsa keys/id_rsa.pub subscriber/keys/
    cp keys/id_rsa keys/id_rsa.pub mpi-worker/keys/
else
    echo "[1/3] SSH keys already exist, skipping generation."
fi

# Build all containers
echo "[2/3] Building Docker images (this may take several minutes on first run)..."
docker compose build

# Start all containers
echo "[3/3] Starting all containers..."
docker compose up -d

echo ""
echo "=== All containers started ==="
echo ""
echo "  Frontend:           http://localhost:5173"
echo "  Spring Boot API:    http://localhost:8080"
echo "  Storage API:        http://localhost:3000"
echo "  RabbitMQ Management: http://localhost:15672  (guest/guest)"
echo ""
echo "Usage:"
echo "  1. Open http://localhost:5173 in your browser"
echo "  2. Register a new account (first user becomes admin)"
echo "  3. Upload a BMP image to encrypt/decrypt"
echo ""
echo "To view logs:    docker compose logs -f"
echo "To stop:         docker compose down"
