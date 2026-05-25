#!/bin/bash

# Start SNMP daemon
snmpd -Lo -C -c /etc/snmp/snmpd.conf 2>/dev/null || true

# Wait for mpi-worker SSH to be reachable
echo "Waiting for mpi-worker SSH..."
for i in $(seq 1 60); do
    if ssh -o ConnectTimeout=2 -o BatchMode=yes mpi-worker true 2>/dev/null; then
        echo "mpi-worker SSH is reachable"
        break
    fi
    echo "Waiting for mpi-worker... ($i/60)"
    sleep 2
done

# Start the subscriber Java application
echo "Starting subscriber..."
exec java -jar /app/subscriber/target/subscriber-1.0.0.jar
