-- Solo lectura. Ejecutar antes de crm-v1.sql para contrastar el esquema actual.
SELECT table_name,column_name,data_type,is_nullable
FROM information_schema.columns
WHERE table_schema='public' AND table_name IN ('usuario','rol','permiso','rol_permiso','cliente','campana')
ORDER BY table_name,ordinal_position;

SELECT conrelid::regclass AS tabla,conname AS restriccion,pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE conrelid IN ('public.usuario'::regclass,'public.rol'::regclass,'public.permiso'::regclass,
 'public.rol_permiso'::regclass,'public.cliente'::regclass,'public.campana'::regclass)
ORDER BY tabla,restriccion;

SELECT r.nombre_rol,p.codigo
FROM public.rol r LEFT JOIN public.rol_permiso rp USING(id_rol) LEFT JOIN public.permiso p USING(id_permiso)
ORDER BY r.nombre_rol,p.codigo;
