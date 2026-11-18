-- Una empresa de la agencia y su ficha CRM comparten identidad comercial.
-- Los IDs históricos permanecen; los perfiles duplicados explícitamente vinculados
-- se conservan como alias. No se deduplican empresas por semejanza de nombres.
BEGIN;
LOCK TABLE public.cliente,public.crm_cuenta IN SHARE ROW EXCLUSIVE MODE;
CREATE TABLE public.crm_empresa_migracion (
 tipo varchar(40) NOT NULL,registro_id bigint NOT NULL,datos jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(tipo,registro_id)
);
ALTER TABLE public.crm_empresa_migracion ENABLE ROW LEVEL SECURITY;
INSERT INTO public.crm_empresa_migracion(tipo,registro_id,datos)
 SELECT 'cliente',id_cliente,to_jsonb(c) FROM public.cliente c;
INSERT INTO public.crm_empresa_migracion(tipo,registro_id,datos)
 SELECT 'cuenta',id,to_jsonb(c) FROM public.crm_cuenta c;
ALTER TABLE public.crm_cuenta ADD COLUMN fusionada_en bigint REFERENCES public.crm_cuenta(id);
DO $$ DECLARE cuenta_previa record; nuevo bigint; t text; BEGIN
 FOR cuenta_previa IN SELECT * FROM public.crm_cuenta WHERE cliente_id IS NULL ORDER BY id LOOP
   INSERT INTO public.cliente(nombre,estado,created_at,updated_at)
     VALUES(left(cuenta_previa.nombre,150),'ACTIVO',cuenta_previa.created_at,cuenta_previa.updated_at) RETURNING id_cliente INTO nuevo;
   UPDATE public.crm_cuenta SET cliente_id=nuevo WHERE id=cuenta_previa.id;
 END LOOP;
 INSERT INTO public.crm_cuenta(nombre,cliente_id,created_at,updated_at)
   SELECT cl.nombre,cl.id_cliente,cl.created_at,cl.updated_at FROM public.cliente cl
   WHERE NOT EXISTS(SELECT 1 FROM public.crm_cuenta a WHERE a.cliente_id=cl.id_cliente);
 UPDATE public.crm_cuenta a SET fusionada_en=p.id FROM
   (SELECT cliente_id,min(id) id FROM public.crm_cuenta GROUP BY cliente_id) p
   WHERE a.cliente_id=p.cliente_id AND a.id<>p.id;
 UPDATE public.crm_cuenta a SET nombre=cl.nombre,
   sector=coalesce(a.sector,(SELECT x.sector FROM public.crm_cuenta x WHERE x.cliente_id=a.cliente_id AND x.sector IS NOT NULL ORDER BY x.id LIMIT 1)),
   sitio_web=coalesce(a.sitio_web,(SELECT x.sitio_web FROM public.crm_cuenta x WHERE x.cliente_id=a.cliente_id AND x.sitio_web IS NOT NULL ORDER BY x.id LIMIT 1))
   FROM public.cliente cl WHERE a.cliente_id=cl.id_cliente AND a.fusionada_en IS NULL;
 FOREACH t IN ARRAY ARRAY['crm_contacto','crm_nota','crm_evento','crm_archivo','crm_actividad'] LOOP
   EXECUTE format('INSERT INTO public.crm_empresa_migracion(tipo,registro_id,datos) SELECT %L,r.id,jsonb_build_object(''cuenta_id'',r.cuenta_id) FROM public.%I r JOIN public.crm_cuenta a ON a.id=r.cuenta_id WHERE a.fusionada_en IS NOT NULL',t,t);
   EXECUTE format('UPDATE public.%I r SET cuenta_id=a.fusionada_en FROM public.crm_cuenta a WHERE r.cuenta_id=a.id AND a.fusionada_en IS NOT NULL',t);
 END LOOP;
 INSERT INTO public.crm_empresa_migracion(tipo,registro_id,datos)
   SELECT 'crm_contacto',c.id,jsonb_build_object('cuenta_id',c.cuenta_id)
   FROM public.crm_contacto c WHERE c.cuenta_id IS NULL AND c.campana_id IS NOT NULL;
 UPDATE public.crm_contacto c SET cuenta_id=a.id FROM public.campana ca JOIN public.crm_cuenta a
   ON a.cliente_id=ca.id_cliente AND a.fusionada_en IS NULL
   WHERE c.cuenta_id IS NULL AND c.campana_id=ca.id_campana;
END $$;
ALTER TABLE public.crm_cuenta ALTER COLUMN cliente_id SET NOT NULL;
CREATE UNIQUE INDEX crm_empresa_cliente_unico ON public.crm_cuenta(cliente_id) WHERE fusionada_en IS NULL;

-- Compatibilidad: crear/editar desde las API de clientes anteriores mantiene
-- automáticamente la ficha CRM; las escrituras de cuentas mantienen el nombre.
CREATE FUNCTION public.crm_cliente_ficha() RETURNS trigger LANGUAGE plpgsql SET search_path=public,pg_temp AS $$
BEGIN
 IF pg_trigger_depth()>1 THEN RETURN NEW; END IF;
 INSERT INTO public.crm_cuenta(nombre,cliente_id) VALUES(NEW.nombre,NEW.id_cliente)
   ON CONFLICT(cliente_id) WHERE fusionada_en IS NULL DO NOTHING;
 UPDATE public.crm_cuenta SET nombre=NEW.nombre,updated_at=NEW.updated_at
   WHERE cliente_id=NEW.id_cliente AND fusionada_en IS NULL AND nombre IS DISTINCT FROM NEW.nombre;
 RETURN NEW;
END $$;
CREATE TRIGGER crm_cliente_ficha AFTER INSERT OR UPDATE ON public.cliente
 FOR EACH ROW EXECUTE FUNCTION public.crm_cliente_ficha();
CREATE FUNCTION public.crm_cuenta_empresa() RETURNS trigger LANGUAGE plpgsql SET search_path=public,pg_temp AS $$
BEGIN
 IF pg_trigger_depth()>1 THEN RETURN NEW; END IF;
 IF TG_OP='UPDATE' AND (NEW.cliente_id IS DISTINCT FROM OLD.cliente_id OR NEW.fusionada_en IS DISTINCT FROM OLD.fusionada_en) THEN
   RAISE EXCEPTION 'La identidad de una empresa no se puede reasignar' USING ERRCODE='23514';
 END IF;
 IF NEW.cliente_id IS NULL THEN
   IF length(NEW.nombre)>150 THEN RAISE EXCEPTION 'Nombre de empresa demasiado largo' USING ERRCODE='23514'; END IF;
   INSERT INTO public.cliente(nombre,estado,created_at,updated_at) VALUES(NEW.nombre,'ACTIVO',now(),now()) RETURNING id_cliente INTO NEW.cliente_id;
 ELSIF TG_OP='INSERT' THEN
   SELECT nombre INTO NEW.nombre FROM public.cliente WHERE id_cliente=NEW.cliente_id;
 ELSE
   UPDATE public.cliente SET nombre=NEW.nombre,updated_at=now() WHERE id_cliente=NEW.cliente_id AND nombre IS DISTINCT FROM NEW.nombre;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER crm_cuenta_empresa BEFORE INSERT OR UPDATE ON public.crm_cuenta
 FOR EACH ROW EXECUTE FUNCTION public.crm_cuenta_empresa();
CREATE FUNCTION public.crm_contacto_empresa() RETURNS trigger LANGUAGE plpgsql SET search_path=public,pg_temp AS $$
DECLARE principal bigint;
BEGIN
 IF NEW.cuenta_id IS NOT NULL THEN
   SELECT coalesce(fusionada_en,id) INTO principal FROM public.crm_cuenta WHERE id=NEW.cuenta_id;
   IF principal IS NOT NULL THEN NEW.cuenta_id=principal; END IF;
 ELSIF NEW.campana_id IS NOT NULL THEN
   SELECT a.id INTO NEW.cuenta_id FROM public.campana ca JOIN public.crm_cuenta a
     ON a.cliente_id=ca.id_cliente AND a.fusionada_en IS NULL WHERE ca.id_campana=NEW.campana_id;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER crm_contacto_empresa BEFORE INSERT OR UPDATE OF cuenta_id,campana_id ON public.crm_contacto
 FOR EACH ROW EXECUTE FUNCTION public.crm_contacto_empresa();
COMMENT ON TABLE public.crm_empresa_migracion IS 'Respaldo de identidades y referencias anteriores a CRM v9. Solo propietario; no contiene credenciales.';
COMMIT;
