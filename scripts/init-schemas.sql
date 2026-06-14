-- Run once on first postgres startup.
-- Creates all schemas so each service's Flyway migration finds its home.
CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS onboarding;
CREATE SCHEMA IF NOT EXISTS banking;
CREATE SCHEMA IF NOT EXISTS kyc_aml;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS audit;
