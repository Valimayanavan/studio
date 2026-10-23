-- =====================================================================
-- studio_admin  -  MySQL 8 schema (InnoDB, utf8mb4)
-- Executed automatically at startup (CREATE TABLE IF NOT EXISTS, so it is
-- safe to run any number of times). Table names are all lowercase because
-- MySQL on Linux treats table names as case-sensitive.
-- =====================================================================

CREATE TABLE IF NOT EXISTS `admin` (
    UserName  VARCHAR(100) NOT NULL,
    Password  VARCHAR(255) NOT NULL,
    PRIMARY KEY (UserName)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `studio` (
    Studio_ID      VARCHAR(191) NOT NULL,
    Studio_name    VARCHAR(255) NOT NULL,
    Studio_mobile  BIGINT       NOT NULL,
    Studio_Email   VARCHAR(255) NOT NULL,
    Owner_name     VARCHAR(255) NOT NULL,
    Password       VARCHAR(255) NULL,
    First_Login    TINYINT      NOT NULL DEFAULT 1,
    Studio_Logo    LONGBLOB     NULL,
    PRIMARY KEY (Studio_ID),
    KEY idx_studio_email (Studio_Email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `customer` (
    customer_id    INT          NOT NULL AUTO_INCREMENT,
    customer_name  VARCHAR(255) NOT NULL,
    phone          VARCHAR(50)  NULL,
    address        TEXT         NULL,
    studio_ID      VARCHAR(191) NULL,
    PRIMARY KEY (customer_id),
    KEY idx_customer_studio (studio_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `customer_images` (
    image_id     INT          NOT NULL AUTO_INCREMENT,
    customer_id  INT          NOT NULL,
    image_name   VARCHAR(255) NOT NULL,
    image_data   LONGBLOB     NOT NULL,
    uploaded_by  VARCHAR(20)  NOT NULL DEFAULT 'STUDIO',
    PRIMARY KEY (image_id),
    KEY idx_images_customer (customer_id),
    KEY idx_images_customer_uploader (customer_id, uploaded_by),
    CONSTRAINT fk_images_customer FOREIGN KEY (customer_id)
        REFERENCES `customer` (customer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy table name retained; tokens now grant access to the private customer gallery portal.
CREATE TABLE IF NOT EXISTS `customer_selection_links` (
    link_id      INT          NOT NULL AUTO_INCREMENT,
    customer_id  INT          NOT NULL,
    token        VARCHAR(100) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (link_id),
    UNIQUE KEY uq_selection_token (token),
    KEY idx_sellinks_customer (customer_id),
    CONSTRAINT fk_sellinks_customer FOREIGN KEY (customer_id)
        REFERENCES `customer` (customer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Historical selections are retained for existing records; the active portal does not use this table.
CREATE TABLE IF NOT EXISTS `customer_selected_images` (
    selection_id  INT          NOT NULL AUTO_INCREMENT,
    token         VARCHAR(100) NOT NULL,
    customer_id   INT          NOT NULL,
    image_id      INT          NOT NULL,
    PRIMARY KEY (selection_id),
    UNIQUE KEY uq_selected_token_image (token, image_id),
    KEY idx_selected_customer (customer_id),
    KEY idx_selected_image (image_id),
    CONSTRAINT fk_selected_customer FOREIGN KEY (customer_id)
        REFERENCES `customer` (customer_id) ON DELETE CASCADE,
    CONSTRAINT fk_selected_image FOREIGN KEY (image_id)
        REFERENCES `customer_images` (image_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Canonical album storage used by both studio uploads and the customer gallery portal.
CREATE TABLE IF NOT EXISTS `customer_albums` (
    album_id     INT          NOT NULL AUTO_INCREMENT,
    customer_id  INT          NOT NULL,
    album_name   VARCHAR(255) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    token        VARCHAR(100) NOT NULL,
    PRIMARY KEY (album_id),
    UNIQUE KEY uq_album_token (token),
    KEY idx_albums_customer (customer_id),
    CONSTRAINT fk_albums_customer FOREIGN KEY (customer_id)
        REFERENCES `customer` (customer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `customer_album_images` (
    album_image_id  INT NOT NULL AUTO_INCREMENT,
    album_id        INT NOT NULL,
    image_id        INT NOT NULL,
    PRIMARY KEY (album_image_id),
    UNIQUE KEY uq_album_image (album_id, image_id),
    KEY idx_albumimages_image (image_id),
    CONSTRAINT fk_albumimages_album FOREIGN KEY (album_id)
        REFERENCES `customer_albums` (album_id) ON DELETE CASCADE,
    CONSTRAINT fk_albumimages_image FOREIGN KEY (image_id)
        REFERENCES `customer_images` (image_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `customer_album_links` (
    link_id      INT          NOT NULL AUTO_INCREMENT,
    customer_id  INT          NOT NULL,
    token        VARCHAR(100) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active       TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (link_id),
    UNIQUE KEY uq_albumlink_token (token),
    KEY idx_albumlinks_customer (customer_id),
    CONSTRAINT fk_albumlinks_customer FOREIGN KEY (customer_id)
        REFERENCES `customer` (customer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy table retained for non-destructive migration of old customer folders.
CREATE TABLE IF NOT EXISTS `customer_photo_folders` (
    folder_id    INT          NOT NULL AUTO_INCREMENT,
    customer_id  INT          NOT NULL,
    folder_name  VARCHAR(255) NOT NULL,
    token        VARCHAR(100) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (folder_id),
    UNIQUE KEY uq_photo_folder_token (token),
    KEY idx_photo_folders_customer (customer_id),
    CONSTRAINT fk_photo_folders_customer FOREIGN KEY (customer_id)
        REFERENCES `customer` (customer_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy table retained until existing folder albums have been migrated.
CREATE TABLE IF NOT EXISTS `customer_photo_folder_images` (
    image_id    INT          NOT NULL AUTO_INCREMENT,
    folder_id   INT          NOT NULL,
    image_name  VARCHAR(255) NOT NULL,
    image_data  LONGBLOB     NOT NULL,
    PRIMARY KEY (image_id),
    KEY idx_folderimages_folder (folder_id),
    CONSTRAINT fk_folderimages_folder FOREIGN KEY (folder_id)
        REFERENCES `customer_photo_folders` (folder_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `schema_migrations` (
    version      INT          NOT NULL,
    description  VARCHAR(255) NOT NULL,
    applied_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `subscription` (
    id                   INT          NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(255) NOT NULL,
    phone                VARCHAR(50)  NOT NULL,
    email                VARCHAR(255) NOT NULL,
    plan                 VARCHAR(30)  NOT NULL,
    amount               INT          NOT NULL,
    razorpay_order_id    VARCHAR(64)  NULL,
    razorpay_payment_id  VARCHAR(64)  NULL,
    razorpay_signature   VARCHAR(255) NULL,
    start_date           DATE         NULL,
    expiry_date          DATE         NULL,
    trial_start_date     DATE         NULL,
    trial_end_date       DATE         NULL,
    studio_ID            VARCHAR(191) NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_subscription_order (razorpay_order_id),
    KEY idx_subscription_email (email),
    KEY idx_subscription_status (status),
    KEY idx_subscription_studio (studio_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
