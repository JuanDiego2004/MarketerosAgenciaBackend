BEGIN;
ALTER TABLE public.crm_alta_contrato DROP CONSTRAINT crm_alta_contrato_contrato_id_key;
CREATE INDEX crm_alta_contrato_confirmado ON public.crm_alta_contrato(contrato_id);
CREATE TABLE public.crm_contrato_ampliacion (
 id uuid PRIMARY KEY REFERENCES public.crm_alta_contrato(id),contrato_id bigint NOT NULL REFERENCES public.crm_acuerdo(id),
 datos jsonb NOT NULL,autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario),created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.crm_contrato_ampliacion ENABLE ROW LEVEL SECURITY;
COMMIT;
