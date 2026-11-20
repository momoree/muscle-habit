-- MuscleHabit PC 版のデータベースとユーザーを作る（最初に1回だけ）。
-- MySQL Workbench か、root で入った mysql コマンドで実行する。
-- 'ここにパスワード' は自分で決めたパスワードに書き換えること（PC アプリの設定画面にも同じものを入れる）。

CREATE DATABASE IF NOT EXISTS musclehabit
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- この PC の中（localhost）からだけ入れるユーザー
CREATE USER IF NOT EXISTS 'musclehabit'@'localhost' IDENTIFIED BY 'ここにパスワード';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER ON musclehabit.* TO 'musclehabit'@'localhost';
FLUSH PRIVILEGES;
