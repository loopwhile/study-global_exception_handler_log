CREATE DATABASE geh_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'geh_user'@'%' IDENTIFIED BY 'geh_pass';
GRANT ALL PRIVILEGES ON geh_db.* TO 'geh_user'@'%';
FLUSH PRIVILEGES;