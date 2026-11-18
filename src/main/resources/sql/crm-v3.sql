-- Expandir sin retirar etiquetas/campana_id anteriores. Compatible con lectores/escritores v2.
BEGIN;
CREATE TABLE public.crm_etiqueta (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 nombre varchar(500) NOT NULL CHECK(btrim(nombre)<>'' AND nombre !~ ','),
 color varchar(7) NOT NULL DEFAULT '#1D4ED8' CHECK(color ~ '^#[0-9a-fA-F]{6}$')
);
CREATE UNIQUE INDEX crm_etiqueta_nombre_unico ON public.crm_etiqueta(lower(btrim(nombre)));
CREATE TABLE public.crm_contacto_etiqueta (
 contacto_id bigint NOT NULL REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 etiqueta_id bigint NOT NULL REFERENCES public.crm_etiqueta(id),
 PRIMARY KEY(contacto_id,etiqueta_id)
);
CREATE FUNCTION public.crm_sincronizar_etiquetas() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE etiqueta text; clave bigint;
BEGIN
 DELETE FROM public.crm_contacto_etiqueta WHERE contacto_id=NEW.id;
 FOR etiqueta IN SELECT DISTINCT btrim(t) FROM unnest(string_to_array(NEW.etiquetas,',')) t WHERE btrim(t)<>'' ORDER BY btrim(t) LOOP
   INSERT INTO public.crm_etiqueta(nombre) VALUES(etiqueta)
   ON CONFLICT(lower(btrim(nombre))) DO UPDATE SET nombre=crm_etiqueta.nombre RETURNING id INTO clave;
   INSERT INTO public.crm_contacto_etiqueta VALUES(NEW.id,clave) ON CONFLICT DO NOTHING;
 END LOOP;
 RETURN NEW;
END $$;
CREATE TRIGGER crm_contacto_etiquetas AFTER INSERT OR UPDATE OF etiquetas ON public.crm_contacto
 FOR EACH ROW EXECUTE FUNCTION public.crm_sincronizar_etiquetas();
-- Backfill sin modificar la representación antigua ni updated_at.
INSERT INTO public.crm_etiqueta(nombre)
SELECT min(btrim(t)) FROM public.crm_contacto c CROSS JOIN LATERAL unnest(string_to_array(c.etiquetas,',')) t
WHERE btrim(t)<>'' GROUP BY lower(btrim(t)) ORDER BY lower(btrim(t));
INSERT INTO public.crm_contacto_etiqueta(contacto_id,etiqueta_id)
SELECT DISTINCT c.id,e.id FROM public.crm_contacto c
CROSS JOIN LATERAL unnest(string_to_array(c.etiquetas,',')) t
JOIN public.crm_etiqueta e ON lower(btrim(e.nombre))=lower(btrim(t)) WHERE btrim(t)<>'';

CREATE TABLE public.crm_transicion (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 oportunidad_id bigint NOT NULL REFERENCES public.crm_oportunidad(id) ON DELETE CASCADE,
 etapa_anterior_id bigint, etapa_id bigint NOT NULL REFERENCES public.crm_etapa(id),
 etapa_nombre varchar(160) NOT NULL, etapa_tipo varchar(10) NOT NULL, etapa_orden int NOT NULL,
 fecha timestamptz NOT NULL DEFAULT now(), linea_base boolean NOT NULL DEFAULT false,
 autor_id bigint REFERENCES public.usuario(id_usuario)
);
CREATE INDEX crm_transicion_oportunidad_fecha ON public.crm_transicion(oportunidad_id,fecha,id);
CREATE FUNCTION public.crm_registrar_transicion() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE anterior bigint; actor bigint;
BEGIN
 IF TG_OP='UPDATE' THEN
   IF NEW.etapa_id=OLD.etapa_id THEN RETURN NEW; END IF;
   anterior=OLD.etapa_id;
 END IF;
 actor=nullif(current_setting('crm.autor_id',true),'')::bigint;
 INSERT INTO public.crm_transicion(oportunidad_id,etapa_anterior_id,etapa_id,etapa_nombre,etapa_tipo,etapa_orden,autor_id)
 SELECT NEW.id,anterior,e.id,e.nombre,e.tipo,e.orden,actor FROM public.crm_etapa e WHERE e.id=NEW.etapa_id;
 RETURN NEW;
END $$;
INSERT INTO public.crm_transicion(oportunidad_id,etapa_id,etapa_nombre,etapa_tipo,etapa_orden,linea_base)
SELECT o.id,e.id,e.nombre,e.tipo,e.orden,true FROM public.crm_oportunidad o JOIN public.crm_etapa e ON e.id=o.etapa_id;
CREATE TRIGGER crm_oportunidad_transicion AFTER INSERT OR UPDATE OF etapa_id ON public.crm_oportunidad
 FOR EACH ROW EXECUTE FUNCTION public.crm_registrar_transicion();

-- La supresión prevalece sobre cualquier consentimiento antiguo.
CREATE TABLE public.crm_baja_email (
 contacto_id bigint PRIMARY KEY REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 fecha timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_baja_token (
 hash varchar(64) PRIMARY KEY, contacto_id bigint NOT NULL REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX crm_baja_token_contacto ON public.crm_baja_token(contacto_id);
-- Una campaña principal anterior queda registrada sin inventar otras interacciones.
INSERT INTO public.crm_origen(contacto_id,campana_id,canal,created_at,idempotencia)
SELECT c.id,c.campana_id,'MIGRACION',c.created_at,gen_random_uuid() FROM public.crm_contacto c
WHERE c.campana_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.crm_origen x WHERE x.contacto_id=c.id);
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['crm_etiqueta','crm_contacto_etiqueta','crm_transicion','crm_baja_email','crm_baja_token'] LOOP
 EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
 END LOOP;
END $$;
COMMIT;
