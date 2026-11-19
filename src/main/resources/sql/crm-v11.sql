BEGIN;
CREATE TABLE public.crm_alcance_servicio (
 contrato_id bigint NOT NULL REFERENCES public.crm_acuerdo(id),prestacion varchar(20) NOT NULL CHECK(prestacion IN ('PRODUCCION','EDICION','PUBLICACION','ANALISIS','INFORMES')),
 cantidad int NOT NULL CHECK(cantidad BETWEEN 1 AND 10000),PRIMARY KEY(contrato_id,prestacion)
);
-- Compatibilidad: las condiciones previas no se restringen retroactivamente.
INSERT INTO public.crm_alcance_servicio
 SELECT a.id,p.prestacion,10000 FROM public.crm_acuerdo a CROSS JOIN (VALUES('PRODUCCION'),('EDICION'),('PUBLICACION'),('ANALISIS'),('INFORMES')) p(prestacion)
 WHERE (a.servicio<>'ANALISIS' OR p.prestacion IN ('ANALISIS','INFORMES'));
ALTER TABLE public.crm_trabajo_servicio ADD COLUMN prestacion varchar(20);
CREATE TABLE public.crm_media_anuncio (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,anuncio_id bigint NOT NULL REFERENCES public.crm_anuncio(id),
 nombre varchar(160) NOT NULL,mime varchar(40) NOT NULL,contenido bytea NOT NULL CHECK(octet_length(contenido) BETWEEN 1 AND 20971520),
 autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario),created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX crm_media_por_anuncio ON public.crm_media_anuncio(anuncio_id,id DESC);
CREATE TABLE public.crm_informe_version (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,contrato_id bigint NOT NULL REFERENCES public.crm_acuerdo(id),numero int NOT NULL,
 titulo varchar(160) NOT NULL,conclusiones varchar(10000) NOT NULL,datos jsonb NOT NULL,pdf bytea NOT NULL,
 publicado boolean NOT NULL DEFAULT false,autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario),created_at timestamptz NOT NULL DEFAULT now(),
 idempotencia uuid NOT NULL UNIQUE,UNIQUE(contrato_id,numero)
);
CREATE TABLE public.crm_portal_acceso (
 usuario_id bigint PRIMARY KEY REFERENCES public.usuario(id_usuario),empresa_id bigint NOT NULL REFERENCES public.crm_cuenta(id),
 activo boolean NOT NULL DEFAULT true,version int NOT NULL DEFAULT 0,otorgado_por bigint NOT NULL REFERENCES public.usuario(id_usuario),updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_revision_cliente (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,anuncio_id bigint NOT NULL REFERENCES public.crm_anuncio(id),media_id bigint NOT NULL REFERENCES public.crm_media_anuncio(id),
 estado varchar(15) NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','APROBADO','RECHAZADO','ANULADA')),
 observacion varchar(2000),solicitado_por bigint NOT NULL REFERENCES public.usuario(id_usuario),respondido_por bigint REFERENCES public.usuario(id_usuario),
 created_at timestamptz NOT NULL DEFAULT now(),respondido_en timestamptz,version int NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX crm_revision_pendiente ON public.crm_revision_cliente(anuncio_id) WHERE estado='PENDIENTE';
ALTER TABLE public.crm_alcance_servicio ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_media_anuncio ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_informe_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_portal_acceso ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_revision_cliente ENABLE ROW LEVEL SECURITY;
INSERT INTO public.rol(nombre_rol,descripcion) SELECT 'Cliente','Acceso exclusivo al portal de su empresa' WHERE NOT EXISTS(SELECT 1 FROM public.rol WHERE lower(nombre_rol)='cliente');
COMMIT;
