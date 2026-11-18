BEGIN;

CREATE TABLE IF NOT EXISTS public.marketing_ubicacion (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 pais varchar(100) NOT NULL,
 codigo_pais varchar(2) NOT NULL CHECK(codigo_pais ~ '^[A-Z]{2}$'),
 ciudad varchar(150) NOT NULL DEFAULT '',
 latitud numeric(10,7) NOT NULL CHECK(latitud BETWEEN -90 AND 90),
 longitud numeric(10,7) NOT NULL CHECK(longitud BETWEEN -180 AND 180),
 UNIQUE(codigo_pais,ciudad)
);

CREATE TABLE IF NOT EXISTS public.marketing_cobertura (
 campana_id bigint NOT NULL REFERENCES public.campana(id_campana) ON DELETE CASCADE,
 ubicacion_id bigint NOT NULL REFERENCES public.marketing_ubicacion(id),
 PRIMARY KEY(campana_id,ubicacion_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS marketing_ubicacion_ciudad_normalizada
 ON public.marketing_ubicacion(codigo_pais,lower(ciudad));

-- Un desglose pertenece a una métrica existente. Los totales proceden de esa
-- métrica y nunca se suman de nuevo a partir de sus ubicaciones.
CREATE TABLE IF NOT EXISTS public.marketing_resultado_geografico (
 metrica_id bigint NOT NULL REFERENCES public.metrica_operativa(id_metrica) ON DELETE CASCADE,
 ubicacion_id bigint NOT NULL REFERENCES public.marketing_ubicacion(id),
 impresiones bigint NOT NULL DEFAULT 0 CHECK(impresiones>=0),
 clics bigint NOT NULL DEFAULT 0 CHECK(clics>=0),
 conversiones bigint NOT NULL DEFAULT 0 CHECK(conversiones>=0),
 inversion numeric(20,6) NOT NULL DEFAULT 0 CHECK(inversion>=0),
 ingresos numeric(20,6) NOT NULL DEFAULT 0 CHECK(ingresos>=0),
 PRIMARY KEY(metrica_id,ubicacion_id)
);
CREATE INDEX IF NOT EXISTS marketing_resultado_ubicacion_idx
 ON public.marketing_resultado_geografico(ubicacion_id);
ALTER TABLE public.marketing_ubicacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.marketing_cobertura ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.marketing_resultado_geografico ENABLE ROW LEVEL SECURITY;

-- Las ediciones del módulo de métricas también respetan los desgloses guardados.
CREATE OR REPLACE FUNCTION public.marketing_validar_total_metrica() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE asignado record;
BEGIN
 SELECT coalesce(sum(impresiones),0) impresiones,coalesce(sum(clics),0) clics,
   coalesce(sum(conversiones),0) conversiones,coalesce(sum(inversion),0) inversion,
   coalesce(sum(ingresos),0) ingresos INTO asignado
 FROM public.marketing_resultado_geografico WHERE metrica_id=NEW.id_metrica;
 IF asignado.impresiones>NEW.impresiones OR asignado.clics>NEW.clics
   OR asignado.conversiones>NEW.conversiones OR asignado.inversion>NEW.inversion
   OR asignado.ingresos>NEW.ingresos THEN
   RAISE EXCEPTION 'La métrica tiene un desglose geográfico superior al nuevo total. Ajusta primero el desglose.'
     USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS marketing_total_metrica ON public.metrica_operativa;
CREATE TRIGGER marketing_total_metrica BEFORE UPDATE ON public.metrica_operativa
 FOR EACH ROW EXECUTE FUNCTION public.marketing_validar_total_metrica();
COMMIT;
