DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_cron') THEN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS pg_cron;
            PERFORM cron.schedule('partman-maintenance', '0 * * * *', 'CALL partman.run_maintenance_proc()');
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING 'pg_cron indisponível (%); agende partman.run_maintenance_proc() manualmente', SQLERRM;
        END;
    ELSE
        RAISE WARNING 'pg_cron não instalado; agende partman.run_maintenance_proc() manualmente';
    END IF;
END
$$;
