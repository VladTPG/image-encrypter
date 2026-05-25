#!/bin/bash

mysqld --user=root --daemonize

until mysqladmin ping --silent 2>/dev/null; do
    sleep 1
done

mysql -u root -e "CREATE DATABASE IF NOT EXISTS imageencrypter;"
mysql -u root -e "CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'root';"
mysql -u root -e "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;"
mysql -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root';"
mysql -u root -p'root' -e "FLUSH PRIVILEGES;"

mysql -u root -p'root' imageencrypter -e "
CREATE TABLE IF NOT EXISTS images (
  id INT AUTO_INCREMENT PRIMARY KEY,
  job_id VARCHAR(255),
  original_name VARCHAR(255),
  operation VARCHAR(10),
  mode VARCHAR(10),
  image_data LONGBLOB,
  user_email VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);"

mongod --config /etc/mongod.conf --fork

# Start SNMP daemon
snmpd -Lo -C -c /etc/snmp/snmpd.conf 2>/dev/null || true

# Start Express API server
node /app/server.js &

tail -f /dev/null
