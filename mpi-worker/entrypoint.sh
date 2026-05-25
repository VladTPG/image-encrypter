#!/bin/bash

# Start SNMP daemon
snmpd -Lo -C -c /etc/snmp/snmpd.conf 2>/dev/null || true

# Start SSH daemon (foreground, keeps container alive)
echo "Starting sshd..."
/usr/sbin/sshd -D
