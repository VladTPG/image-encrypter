#!/bin/bash

mysqld --user=root --daemonize

until mysqladmin ping --silent 2>/dev/null; do
    sleep 1
done

mysql -u root -e "CREATE DATABASE IF NOT EXISTS imageencrypter;"
mysql -u root -e "CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'root';"
mysql -u root -e "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;"
mysql -u root -e "FLUSH PRIVILEGES;"

mongod --config /etc/mongod.conf --fork

tail -f /dev/null
