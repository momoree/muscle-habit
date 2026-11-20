-- MuscleHabit PC 版のテーブル（MySQL 5.7 / 8）。
-- スマホの Room と同じ項目で、どの表も updated_at（最後に変更した日時）と deleted（削除の印）を持つ。
-- 同じ id が届いたら上書きする（INSERT ... ON DUPLICATE KEY UPDATE）。
-- PC アプリは起動時に、無いテーブルだけ同じ形で自動で作る。手で作りたいときはこのファイルを実行する。

USE musclehabit;

-- 通う予定（曜日ごと）
CREATE TABLE IF NOT EXISTS schedules (
  id          VARCHAR(36) NOT NULL,          -- スマホで作った UUID
  day_of_week INT         NOT NULL,          -- 1=月 … 7=日
  time        TIME        NOT NULL,          -- 予定の時刻（19:00:00 など）
  enabled     BOOLEAN     NOT NULL,          -- その曜日に行くか
  updated_at  DATETIME(6) NOT NULL,
  deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id)
);

-- チェックイン（「今日はパス」も type = 'PASS' で残す）
CREATE TABLE IF NOT EXISTS visits (
  id            VARCHAR(36)  NOT NULL,
  date          DATE         NOT NULL,       -- 行った日
  checked_in_at DATETIME(6)  NOT NULL,       -- 押した日時
  type          VARCHAR(10)  NOT NULL,       -- NORMAL / MINIMUM（最小目標）/ PASS
  memo          VARCHAR(500) NULL,
  updated_at    DATETIME(6)  NOT NULL,
  deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id),
  INDEX idx_visits_date (date)
);

-- 体重・体脂肪（1日1件）
CREATE TABLE IF NOT EXISTS body_records (
  id               VARCHAR(36) NOT NULL,
  date             DATE        NOT NULL,
  weight_kg        FLOAT       NOT NULL,
  body_fat_percent FLOAT       NULL,         -- 体組成計がなければ空
  updated_at       DATETIME(6) NOT NULL,
  deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id),
  INDEX idx_body_records_date (date)
);

-- スマホから届く設定（週の目標回数・通知の時間・週の始まり）
CREATE TABLE IF NOT EXISTS app_settings (
  name  VARCHAR(50)  NOT NULL,
  value VARCHAR(200) NOT NULL,
  PRIMARY KEY (name)
);

-- 例：今週（月曜始まり）に行った日数
-- SELECT COUNT(DISTINCT date) FROM visits
--  WHERE deleted = FALSE AND type <> 'PASS'
--    AND date >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY);
