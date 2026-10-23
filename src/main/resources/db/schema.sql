-- =====================================================
-- Studio Admin - MySQL 8 schema
-- Safe to run repeatedly (CREATE TABLE IF NOT EXISTS).
-- Statements are separated by ';' at end of line.
-- =====================================================

CREATE TABLE IF NOT EXISTS admin (
    admin_id  INT AUTO_INCREMENT PRIMARY KEY,
    UserName  VARCHAR(100) NOT NULL UNIQUE,
    Password  VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Studio_ID / studio_ID use a binary collation so IDs stay
-- case-sensitive, exactly like they were in SQLite.
CREATE TABLE IF NOT EXISTS studio (
    Studio_ID      VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    Studio_name    VARCHAR(255) NOT NULL,
    Studio_mobile  BIGINT       NOT NULL,
    Studio_Email   VARCHAR(255) NOT NULL,
    Owner_name     VARCHAR(255) NOT NULL,
    Password       VARCHAR(255),
    First_Login    TINYINT      NOT NULL DEFAULT 1,
    Studio_Logo    LONGBLOB
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer (
    customer_id    INT AUTO_INCREMENT PRIMARY KEY,
    customer_name  VARCHAR(255) NOT NULL,
    phone          VARCHAR(30),
    address        TEXT,
    studio_ID      VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
    INDEX idx_customer_studio (studio_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_images (
    image_id     INT AUTO_INCREMENT PRIMARY KEY,
    customer_id  INT          NOT NULL,
    image_name   VARCHAR(255) NOT NULL,
    image_data   LONGBLOB     NOT NULL,
    INDEX idx_images_customer (customer_id),
    CONSTRAINT fk_images_customer FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_album_links (
    link_id      INT AUTO_INCREMENT PRIMARY KEY,
    customer_id  INT          NOT NULL,
    token        VARCHAR(100) NOT NULL UNIQUE,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    active       TINYINT      NOT NULL DEFAULT 1,
    CONSTRAINT fk_album_links_customer FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_albums (
    album_id     INT AUTO_INCREMENT PRIMARY KEY,
    customer_id  INT          NOT NULL,
    album_name   VARCHAR(255) NOT NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    token        VARCHAR(100) UNIQUE,
    INDEX idx_albums_customer (customer_id),
    CONSTRAINT fk_albums_customer FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_album_images (
    album_image_id  INT AUTO_INCREMENT PRIMARY KEY,
    album_id        INT NOT NULL,
    image_id        INT NOT NULL,
    UNIQUE KEY uq_album_image (album_id, image_id),
    CONSTRAINT fk_album_images_album FOREIGN KEY (album_id)
        REFERENCES customer_albums(album_id) ON DELETE CASCADE,
    CONSTRAINT fk_album_images_image FOREIGN KEY (image_id)
        REFERENCES customer_images(image_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_selection_links (
    link_id      INT AUTO_INCREMENT PRIMARY KEY,
    customer_id  INT          NOT NULL,
    token        VARCHAR(100) NOT NULL UNIQUE,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_selection_links_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS customer_selected_images (
    selection_id  INT AUTO_INCREMENT PRIMARY KEY,
    token         VARCHAR(100) NOT NULL,
    customer_id   INT NOT NULL,
    image_id      INT NOT NULL,
    UNIQUE KEY uq_selected_token_image (token, image_id),
    INDEX idx_selected_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy tables (not referenced by the current code, kept so no data is lost)
CREATE TABLE IF NOT EXISTS customer_selection (
    selection_id  INT AUTO_INCREMENT PRIMARY KEY,
    customer_id   INT NOT NULL,
    image_id      INT NOT NULL,
    selected_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_selection_customer_image (customer_id, image_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS album_image (
    album_image_id  INT AUTO_INCREMENT PRIMARY KEY,
    album_id        INT NOT NULL,
    image_id        INT NOT NULL,
    UNIQUE KEY uq_legacy_album_image (album_id, image_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS subscription (
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL,
    phone                VARCHAR(30)  NOT NULL,
    email                VARCHAR(255) NOT NULL,
    plan                 VARCHAR(50)  NOT NULL,
    amount               INT          NOT NULL,
    razorpay_order_id    VARCHAR(100) UNIQUE,
    razorpay_payment_id  VARCHAR(100),
    razorpay_signature   VARCHAR(255),
    start_date           DATE,
    expiry_date          DATE,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at           DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_subscription_email (email),
    INDEX idx_subscription_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
