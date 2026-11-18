BEGIN;
CREATE TABLE public.crm_inversion (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 campana_id bigint NOT NULL REFERENCES public.campana(id_campana),
 fecha date NOT NULL,moneda varchar(3) NOT NULL CHECK(moneda IN ('PEN','USD')),
 concepto varchar(200) NOT NULL CHECK(length(btrim(concepto))>0),
 importe numeric(20,2) NOT NULL CHECK(importe>0),
 idempotencia uuid NOT NULL UNIQUE,
 creado_por bigint NOT NULL REFERENCES public.usuario(id_usuario),
 created_at timestamptz NOT NULL DEFAULT now(),anulado boolean NOT NULL DEFAULT false,version int NOT NULL DEFAULT 0
);
CREATE INDEX crm_inversion_campana_fecha ON public.crm_inversion(campana_id,fecha);
ALTER TABLE public.crm_inversion ENABLE ROW LEVEL SECURITY;
COMMENT ON TABLE public.crm_inversion IS 'Gastos adicionales por campaña; no duplicar la pauta que ya llega de la fuente publicitaria.';
COMMIT;
