@echo off
REM ISM HTC/HPC Security - Image Encrypter
REM Build and run all Docker containers

echo === Image Encrypter - Build ^& Run ===
echo.

REM Generate SSH keys for MPI communication if they don't exist
if not exist keys\id_rsa (
    echo [1/3] Generating SSH keys for MPI cross-container communication...
    mkdir keys 2>nul
    ssh-keygen -t rsa -f keys/id_rsa -N "" -q
    mkdir subscriber\keys 2>nul
    mkdir mpi-worker\keys 2>nul
    copy keys\id_rsa subscriber\keys\id_rsa >nul
    copy keys\id_rsa.pub subscriber\keys\id_rsa.pub >nul
    copy keys\id_rsa mpi-worker\keys\id_rsa >nul
    copy keys\id_rsa.pub mpi-worker\keys\id_rsa.pub >nul
) else (
    echo [1/3] SSH keys already exist, skipping generation.
)

REM Build all containers
echo [2/3] Building Docker images (this may take several minutes on first run)...
docker compose build
if %ERRORLEVEL% neq 0 (
    echo ERROR: Docker build failed.
    pause
    exit /b 1
)

REM Start all containers
echo [3/3] Starting all containers...
docker compose up -d
if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to start containers.
    pause
    exit /b 1
)

echo.
echo === All containers started ===
echo.
echo   Frontend:            http://localhost:5173
echo   Spring Boot API:     http://localhost:8080
echo   Storage API:         http://localhost:3000
echo   RabbitMQ Management: http://localhost:15672  (guest/guest)
echo.
echo Usage:
echo   1. Open http://localhost:5173 in your browser
echo   2. Register a new account (first user becomes admin)
echo   3. Upload a BMP image to encrypt/decrypt
echo.
echo To view logs:    docker compose logs -f
echo To stop:         docker compose down
pause
