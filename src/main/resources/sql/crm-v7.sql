BEGIN;
-- Datos exclusivamente simulados. Nunca se incorporan al gasto ni a la atribución reales.
CREATE TABLE public.crm_publicidad_demo (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 campana_id bigint NOT NULL REFERENCES public.campana(id_campana),
 codigo varchar(80) NOT NULL UNIQUE,
 plataforma varchar(12) NOT NULL CHECK(plataforma IN ('META','TIKTOK','YOUTUBE')),
 moneda varchar(3) NOT NULL CHECK(moneda IN ('PEN','USD')),
 creado_por bigint NOT NULL REFERENCES public.usuario(id_usuario),
 created_at timestamptz NOT NULL DEFAULT now(),
 sincronizado_en timestamptz,
 ultimo_error varchar(300),
 UNIQUE(campana_id,plataforma)
);
CREATE TABLE public.crm_publicidad_demo_metrica (
 demo_id bigint NOT NULL REFERENCES public.crm_publicidad_demo(id) ON DELETE CASCADE,
 fecha date NOT NULL,
 pais varchar(2) NOT NULL CHECK(pais IN ('PE','CO','MX','ES','US','BR','JP','ZA','AU')),
 impresiones bigint NOT NULL CHECK(impresiones>=0),
 clics bigint NOT NULL CHECK(clics>=0 AND clics<=impresiones),
 conversiones bigint NOT NULL CHECK(conversiones>=0 AND conversiones<=clics),
 inversion numeric(20,2) NOT NULL CHECK(inversion>=0),
 ingresos numeric(20,2) NOT NULL CHECK(ingresos>=0),
 PRIMARY KEY(demo_id,fecha,pais)
);
ALTER TABLE public.crm_publicidad_demo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_publicidad_demo_metrica ENABLE ROW LEVEL SECURITY;
COMMENT ON TABLE public.crm_publicidad_demo IS 'Campañas ficticias generadas con Mockaroo. No son anuncios publicados.';
COMMENT ON TABLE public.crm_publicidad_demo_metrica IS 'Estadísticas simuladas por día y país, aisladas de las métricas reales.';
COMMIT;
