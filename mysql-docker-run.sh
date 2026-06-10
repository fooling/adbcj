#!/bin/bash

docker rm -f adbcj-test-mysql
# see mysqltestdb for building this image
docker run --name adbcj-test-mysql -p 3306:3306 -d adbcjmysql


docker run --name myadmin -d --link adbcj-test-mysql:db -p 8082:80 --env MYSQL_ROOT_PASSWORD=adbcjtck phpmyadmin/phpmyadmin

docker ps -a | awk '{print $1}' | xargs docker rm -f