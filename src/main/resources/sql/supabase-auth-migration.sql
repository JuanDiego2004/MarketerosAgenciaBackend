-- Ejecutar una sola vez en el SQL Editor de Supabase antes de iniciar el backend actualizado.
ALTER TABLE public.usuario
    ADD COLUMN IF NOT EXISTS id_supabase UUID;

CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_id_supabase
    ON public.usuario (id_supabase)
    WHERE id_supabase IS NOT NULL;

-- Supabase Auth será el único responsable de almacenar y validar contraseñas.
ALTER TABLE public.usuario
    ALTER COLUMN password_hash DROP NOT NULL;
