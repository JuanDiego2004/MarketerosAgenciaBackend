BEGIN;
CREATE TABLE public.crm_servicio (
 codigo varchar(20) PRIMARY KEY,nombre varchar(120) NOT NULL,descripcion varchar(500) NOT NULL,activo boolean NOT NULL DEFAULT true
);
INSERT INTO public.crm_servicio VALUES
 ('DIFUSION','Gestión y difusión','Planificación, contenido, aprobaciones y publicaciones simuladas',true),
 ('ANALISIS','Monitoreo y análisis','Consulta de anuncios, estadísticas e informes sin modificar publicidad',true),
 ('INTEGRAL','Gestión integral','Ejecución, análisis y ajustes autorizados dentro del CRM',true);
CREATE TABLE public.crm_acuerdo (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,tipo varchar(12) NOT NULL CHECK(tipo IN ('COTIZACION','CONTRATO')),
 empresa_id bigint NOT NULL REFERENCES public.crm_cuenta(id),origen_id bigint REFERENCES public.crm_acuerdo(id),
 nombre varchar(160) NOT NULL,servicio varchar(20) NOT NULL REFERENCES public.crm_servicio(codigo),
 modalidad varchar(10) NOT NULL CHECK(modalidad IN ('ORGANICA','PAGADA','MIXTA')),
 desde date NOT NULL,hasta date NOT NULL CHECK(hasta>=desde),moneda varchar(3) NOT NULL CHECK(moneda IN ('PEN','USD')),
 honorarios numeric(16,2) NOT NULL CHECK(honorarios>=0),presupuesto numeric(16,2) NOT NULL CHECK(presupuesto>=0),
 produccion numeric(16,2) NOT NULL CHECK(produccion>=0),responsable_id bigint NOT NULL REFERENCES public.usuario(id_usuario),
 alcance varchar(4000) NOT NULL,plataformas varchar(300) NOT NULL,condiciones varchar(4000),frecuencia varchar(80) NOT NULL,
 estado varchar(25) NOT NULL DEFAULT 'BORRADOR',version int NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_acuerdo_campana (
 contrato_id bigint NOT NULL REFERENCES public.crm_acuerdo(id),campana_id bigint NOT NULL UNIQUE REFERENCES public.campana(id_campana),PRIMARY KEY(contrato_id,campana_id)
);
CREATE TABLE public.crm_cuenta_publicitaria (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,empresa_id bigint NOT NULL REFERENCES public.crm_cuenta(id),
 nombre varchar(160) NOT NULL,codigo varchar(80) NOT NULL UNIQUE,plataforma varchar(20) NOT NULL,
 moneda varchar(3) NOT NULL CHECK(moneda IN ('PEN','USD')),created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_anuncio (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,contrato_id bigint NOT NULL REFERENCES public.crm_acuerdo(id),
 cuenta_id bigint NOT NULL REFERENCES public.crm_cuenta_publicitaria(id),campana_id bigint NOT NULL REFERENCES public.campana(id_campana),
 codigo varchar(80) NOT NULL UNIQUE,nombre varchar(160) NOT NULL,formato varchar(15) NOT NULL CHECK(formato IN ('VIDEO','IMAGEN','TEXTO')),
 estado varchar(25) NOT NULL DEFAULT 'BORRADOR',contenido_id bigint REFERENCES public.crm_contenido(id),
 fecha date NOT NULL,version int NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_trabajo_servicio (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,contrato_id bigint NOT NULL REFERENCES public.crm_acuerdo(id),
 tipo varchar(20) NOT NULL CHECK(tipo IN ('ENTREGABLE','RECOMENDACION','AJUSTE','INFORME')),
 anuncio_id bigint REFERENCES public.crm_anuncio(id),nombre varchar(160) NOT NULL,descripcion varchar(4000) NOT NULL,
 fecha date NOT NULL,cantidad int NOT NULL DEFAULT 1 CHECK(cantidad BETWEEN 1 AND 10000),
 importe numeric(16,2) NOT NULL DEFAULT 0 CHECK(importe>=0),estado varchar(25) NOT NULL DEFAULT 'PENDIENTE',
 version int NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_anuncio_metrica (
 anuncio_id bigint NOT NULL REFERENCES public.crm_anuncio(id),fecha date NOT NULL,pais varchar(2) NOT NULL,
 impresiones bigint NOT NULL CHECK(impresiones>=0),clics bigint NOT NULL CHECK(clics BETWEEN 0 AND impresiones),
 conversiones bigint NOT NULL CHECK(conversiones BETWEEN 0 AND clics),inversion numeric(20,2) NOT NULL CHECK(inversion>=0),
 ingresos numeric(20,2) NOT NULL CHECK(ingresos>=0),PRIMARY KEY(anuncio_id,fecha,pais)
);
CREATE INDEX crm_acuerdo_empresa ON public.crm_acuerdo(empresa_id);
CREATE INDEX crm_anuncio_contrato ON public.crm_anuncio(contrato_id);
CREATE INDEX crm_trabajo_contrato ON public.crm_trabajo_servicio(contrato_id);
ALTER TABLE public.crm_servicio ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_acuerdo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_acuerdo_campana ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_cuenta_publicitaria ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_anuncio ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_trabajo_servicio ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_anuncio_metrica ENABLE ROW LEVEL SECURITY;
COMMENT ON TABLE public.crm_anuncio_metrica IS 'Datos simulados de Mockaroo por anuncio; no representan ingresos del CRM ni se suman a la pauta agregada anterior.';
COMMIT;
