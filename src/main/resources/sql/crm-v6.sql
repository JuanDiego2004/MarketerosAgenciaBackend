BEGIN;
-- Contrato anterior de envíos se conserva; la entrega verificada tiene su propio historial.
ALTER TABLE public.crm_envio ADD COLUMN phone_id varchar(30);
ALTER TABLE public.crm_envio ADD COLUMN destinatario varchar(128);
CREATE INDEX crm_envio_proveedor ON public.crm_envio(phone_id,proveedor_id) WHERE canal='WHATSAPP';
CREATE TABLE public.crm_whatsapp_mensaje (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 phone_id varchar(30) NOT NULL,proveedor_id varchar(200) NOT NULL,
 remitente varchar(128) NOT NULL,telefono varchar(30),tipo varchar(40) NOT NULL,
 texto varchar(10000) NOT NULL,truncado boolean NOT NULL DEFAULT false,
 contacto_id bigint REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 fecha timestamptz NOT NULL,recibido_en timestamptz NOT NULL DEFAULT now(),
 version int NOT NULL DEFAULT 0,UNIQUE(phone_id,proveedor_id)
);
CREATE INDEX crm_whatsapp_contacto_fecha ON public.crm_whatsapp_mensaje(contacto_id,fecha DESC,id DESC);
CREATE TABLE public.crm_whatsapp_estado (
 phone_id varchar(30) NOT NULL,proveedor_id varchar(200) NOT NULL,
 estado varchar(20) NOT NULL CHECK(estado IN ('SENT','DELIVERED','READ','FAILED')),
 fecha timestamptz NOT NULL,recibido_en timestamptz NOT NULL DEFAULT now(),codigo_error varchar(30),
 PRIMARY KEY(phone_id,proveedor_id,estado)
);
ALTER TABLE public.crm_whatsapp_mensaje ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_whatsapp_estado ENABLE ROW LEVEL SECURITY;
-- READ implica entrega; confirmaciones fuera de orden nunca hacen retroceder el resultado.
CREATE VIEW public.crm_whatsapp_entrega WITH (security_invoker=true) AS
SELECT DISTINCT ON (phone_id,proveedor_id) phone_id,proveedor_id,estado,fecha,codigo_error
FROM public.crm_whatsapp_estado
ORDER BY phone_id,proveedor_id,CASE estado WHEN 'READ' THEN 4 WHEN 'DELIVERED' THEN 3 WHEN 'FAILED' THEN 2 ELSE 1 END DESC;
COMMIT;
