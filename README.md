# Storage

## S3 Storage

```sh
cd localstack
docker-compose up -d
```

Create buckets:

```sh
aws --endpoint-url=http://localhost:4566 --region=us-east-1 s3api create-bucket --bucket input
aws --endpoint-url=http://localhost:4566 --region=us-east-1 s3api create-bucket --bucket output
```

Upload sample output data:

```sh
aws s3 --endpoint-url=http://localhost:4566 cp sample-data/output/1-1657541239896.csv s3://output
aws s3 --endpoint-url=http://localhost:4566 cp sample-data/output/2-1657541240134.csv s3://output
aws s3 --endpoint-url=http://localhost:4566 cp sample-data/output/3-1657541240213.csv s3://output
```

## Database

`docker run -d --name pr_spike_pg -p 5450:5432 -e POSTGRES_PASSWORD=abc123 postgres:13.4`

### Database Schema

```sql
-- Database: postgres

-- DROP DATABASE IF EXISTS postgres;

CREATE DATABASE postgres
WITH
OWNER = postgres
ENCODING = 'UTF8'
LC_COLLATE = 'en_US.utf8'
LC_CTYPE = 'en_US.utf8'
TABLESPACE = pg_default
CONNECTION LIMIT = -1
IS_TEMPLATE = False;

COMMENT ON DATABASE postgres
IS 'default administrative connection database';

-- SCHEMA: public

-- DROP SCHEMA IF EXISTS public ;

CREATE SCHEMA IF NOT EXISTS public
AUTHORIZATION postgres;

COMMENT ON SCHEMA public
IS 'standard public schema';

GRANT ALL ON SCHEMA public TO PUBLIC;

GRANT ALL ON SCHEMA public TO postgres;

-- Table: public.owner

-- DROP TABLE IF EXISTS public.owner;

CREATE TABLE IF NOT EXISTS public.owner
(
id bigint NOT NULL,
name character varying(255) COLLATE pg_catalog."default" NOT NULL,
CONSTRAINT owner_pkey PRIMARY KEY (id)
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.owner
OWNER to postgres;

-- Table: public.pet

-- DROP TABLE IF EXISTS public.pet;

CREATE TABLE IF NOT EXISTS public.pet
(
id bigint NOT NULL,
owner_id bigint NOT NULL,
name character varying(255) COLLATE pg_catalog."default",
CONSTRAINT pet_pk PRIMARY KEY (id),
CONSTRAINT owner_fk FOREIGN KEY (owner_id)
REFERENCES public.owner (id) MATCH SIMPLE
ON UPDATE NO ACTION
ON DELETE NO ACTION
NOT VALID
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS public.pet
OWNER to postgres;
-- Index: fki_owner_fk

-- DROP INDEX IF EXISTS public.fki_owner_fk;

CREATE INDEX IF NOT EXISTS fki_owner_fk
ON public.pet USING btree
(owner_id ASC NULLS LAST)
TABLESPACE pg_default;
```

### Starter Data

```sql
insert into owner values(1, 'Doug');
insert into owner values(2, 'Zach');
insert into owner values(3, 'Tim');

insert into pet values(1, 1, null);
insert into pet values(2, 1, null);
insert into pet values(3, 1, null);
insert into pet values(4, 2, null);
insert into pet values(5, 2, null);
insert into pet values(6, 3, null);
insert into pet values(7, 3, null);
insert into pet values(8, 3, null);
insert into pet values(9, 3, null);
```

Or restore `pets` at the root of the project.
