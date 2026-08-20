CREATE TABLE IF NOT EXISTS etf_stock_info (
    isu_cd VARCHAR(100) NOT NULL,
    isu_nm VARCHAR(100) NOT NULL,
    mkt_nm VARCHAR(20) NOT NULL,
    is_public_stock BOOLEAN NOT NULL,
    PRIMARY KEY (isu_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS etf_daily_trading_information (
    id BIGINT NOT NULL AUTO_INCREMENT,
    date DATE NOT NULL,
    tdd_clsprc INT NOT NULL,
    cmpprevdd_prc INT NOT NULL,
    fluc_rt DOUBLE NOT NULL,
    tdd_opnprc INT NOT NULL,
    tdd_hgprc INT NOT NULL,
    tdd_lwprc INT NOT NULL,
    rsi DOUBLE NULL,
    acc_trdvol BIGINT NOT NULL,
    acc_trdval BIGINT NOT NULL,
    avg_closing_gain DOUBLE NULL,
    avg_closing_loss DOUBLE NULL,
    isu_cd VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_etf_daily_date_isu (date, isu_cd),
    KEY idx_etf_daily_isu_date (isu_cd, date),
    KEY idx_etf_daily_date_rsi (date, rsi),
    CONSTRAINT fk_etf_daily_stock FOREIGN KEY (isu_cd)
        REFERENCES etf_stock_info (isu_cd)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
