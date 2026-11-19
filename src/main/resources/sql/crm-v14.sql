BEGIN;

-- El ámbito procede exclusivamente de la identidad firmada por Supabase Auth.
-- La función devuelve identificadores propios y evita recursión entre políticas.
CREATE OR REPLACE FUNCTION public.crm_portal_ids(recurso text)
RETURNS bigint[] LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE
 empresa bigint;
 contratos bigint[];
 anuncios bigint[];
 resultado bigint[];
BEGIN
 SELECT p.empresa_id INTO empresa
 FROM public.crm_portal_acceso p
 JOIN public.usuario u ON u.id_usuario=p.usuario_id
 JOIN public.rol r ON r.id_rol=u.id_rol
 WHERE u.id_supabase=auth.uid() AND upper(u.estado)='ACTIVO'
   AND lower(r.nombre_rol)='cliente' AND p.activo;
 IF empresa IS NULL THEN RETURN ARRAY[]::bigint[]; END IF;
 IF recurso='EMPRESA' THEN RETURN ARRAY[empresa]; END IF;

 SELECT coalesce(array_agg(c.id),ARRAY[]::bigint[]) INTO contratos
 FROM public.crm_acuerdo c WHERE c.empresa_id=empresa AND c.tipo='CONTRATO'
   AND c.estado NOT IN ('BORRADOR','EN_REVISION');
 IF recurso='CONTRATO' THEN RETURN contratos; END IF;

 IF recurso='CUENTA' THEN
  SELECT coalesce(array_agg(id),ARRAY[]::bigint[]) INTO resultado
  FROM public.crm_cuenta_publicitaria WHERE empresa_id=empresa;
  RETURN resultado;
 END IF;

 SELECT coalesce(array_agg(a.id),ARRAY[]::bigint[]) INTO anuncios
 FROM public.crm_anuncio a
 JOIN public.crm_cuenta_publicitaria p ON p.id=a.cuenta_id AND p.empresa_id=empresa
 JOIN public.crm_acuerdo origen ON origen.id=a.contrato_id AND origen.empresa_id=empresa
 JOIN public.campana ca ON ca.id_campana=a.campana_id
 JOIN public.crm_cuenta e ON e.id=empresa AND e.cliente_id=ca.id_cliente
 WHERE a.contrato_id=ANY(contratos) OR EXISTS(
  SELECT 1 FROM public.crm_contrato_anuncio v
  WHERE v.anuncio_id=a.id AND v.contrato_id=ANY(contratos));
 IF recurso='ANUNCIO' THEN RETURN anuncios; END IF;

 IF recurso='CAMPANA' THEN
  SELECT coalesce(array_agg(DISTINCT c.id_campana),ARRAY[]::bigint[]) INTO resultado
  FROM public.campana c JOIN public.crm_cuenta e ON e.cliente_id=c.id_cliente
  WHERE e.id=empresa AND (
   EXISTS(SELECT 1 FROM public.crm_acuerdo_campana v WHERE v.campana_id=c.id_campana AND v.contrato_id=ANY(contratos))
   OR EXISTS(SELECT 1 FROM public.crm_anuncio a WHERE a.campana_id=c.id_campana AND a.id=ANY(anuncios)));
  RETURN resultado;
 END IF;
 RETURN ARRAY[]::bigint[];
END;
$$;
REVOKE ALL ON FUNCTION public.crm_portal_ids(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.crm_portal_ids(text) TO authenticated;

-- El guardián restrictivo mantiene el aislamiento incluso con otras políticas
-- permisivas existentes. El backend conserva su autorización por usuario.
DO $$
DECLARE regla record;
BEGIN
 FOR regla IN SELECT * FROM (VALUES
  ('crm_cuenta','id IN (SELECT unnest(public.crm_portal_ids(''EMPRESA'')))'),
  ('crm_acuerdo','id IN (SELECT unnest(public.crm_portal_ids(''CONTRATO'')))'),
  ('crm_cuenta_publicitaria','id IN (SELECT unnest(public.crm_portal_ids(''CUENTA'')))'),
  ('crm_anuncio','id IN (SELECT unnest(public.crm_portal_ids(''ANUNCIO'')))'),
  ('campana','id_campana IN (SELECT unnest(public.crm_portal_ids(''CAMPANA'')))'),
  ('crm_anuncio_metrica','anuncio_id IN (SELECT unnest(public.crm_portal_ids(''ANUNCIO'')))'),
  ('crm_acuerdo_campana','contrato_id IN (SELECT unnest(public.crm_portal_ids(''CONTRATO''))) AND campana_id IN (SELECT unnest(public.crm_portal_ids(''CAMPANA'')))'),
  ('crm_contrato_anuncio','contrato_id IN (SELECT unnest(public.crm_portal_ids(''CONTRATO''))) AND anuncio_id IN (SELECT unnest(public.crm_portal_ids(''ANUNCIO'')))')
 ) AS politicas(tabla,condicion) LOOP
  EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',regla.tabla);
  EXECUTE format('DROP POLICY IF EXISTS portal_cliente_lectura ON public.%I',regla.tabla);
  EXECUTE format('DROP POLICY IF EXISTS portal_cliente_aislamiento ON public.%I',regla.tabla);
  EXECUTE format('CREATE POLICY portal_cliente_lectura ON public.%I FOR SELECT TO authenticated USING (%s)',regla.tabla,regla.condicion);
  EXECUTE format('CREATE POLICY portal_cliente_aislamiento ON public.%I AS RESTRICTIVE FOR SELECT TO PUBLIC USING (%s)',regla.tabla,regla.condicion);
  -- El portal es de lectura. Las aprobaciones siguen pasando por el backend.
  EXECUTE format('DROP POLICY IF EXISTS portal_cliente_sin_insertar ON public.%I',regla.tabla);
  EXECUTE format('DROP POLICY IF EXISTS portal_cliente_sin_actualizar ON public.%I',regla.tabla);
  EXECUTE format('DROP POLICY IF EXISTS portal_cliente_sin_eliminar ON public.%I',regla.tabla);
  EXECUTE format('CREATE POLICY portal_cliente_sin_insertar ON public.%I AS RESTRICTIVE FOR INSERT TO PUBLIC WITH CHECK (false)',regla.tabla);
  EXECUTE format('CREATE POLICY portal_cliente_sin_actualizar ON public.%I AS RESTRICTIVE FOR UPDATE TO PUBLIC USING (false) WITH CHECK (false)',regla.tabla);
  EXECUTE format('CREATE POLICY portal_cliente_sin_eliminar ON public.%I AS RESTRICTIVE FOR DELETE TO PUBLIC USING (false)',regla.tabla);
 END LOOP;
END;
$$;

GRANT USAGE ON SCHEMA public TO authenticated;
GRANT SELECT(id,nombre) ON public.crm_cuenta TO authenticated;
GRANT SELECT(id,empresa_id,tipo,nombre,servicio,estado,desde,hasta,moneda,honorarios,produccion,presupuesto,alcance)
 ON public.crm_acuerdo TO authenticated;
GRANT SELECT(id,empresa_id,nombre,codigo,plataforma,moneda) ON public.crm_cuenta_publicitaria TO authenticated;
GRANT SELECT(id,contrato_id,cuenta_id,campana_id,nombre,codigo,estado,fecha) ON public.crm_anuncio TO authenticated;
GRANT SELECT(id_campana,nombre) ON public.campana TO authenticated;
GRANT SELECT(contrato_id,campana_id) ON public.crm_acuerdo_campana TO authenticated;
GRANT SELECT(contrato_id,anuncio_id) ON public.crm_contrato_anuncio TO authenticated;
GRANT SELECT(anuncio_id,fecha,pais,impresiones,clics,conversiones,inversion,ingresos)
 ON public.crm_anuncio_metrica TO authenticated;

CREATE OR REPLACE FUNCTION public.crm_portal_estadisticas(
 p_desde date DEFAULT current_date-29,
 p_hasta date DEFAULT current_date,
 p_contrato bigint DEFAULT NULL,
 p_campana bigint DEFAULT NULL,
 p_plataforma text DEFAULT NULL,
 p_pais text DEFAULT NULL,
 p_moneda text DEFAULT NULL,
 p_busqueda text DEFAULT ''
) RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path = '' AS $$
DECLARE resultado jsonb;
BEGIN
 IF cardinality(public.crm_portal_ids('EMPRESA'))<>1 THEN
  RAISE EXCEPTION 'Tu usuario necesita un acceso activo al portal de su empresa' USING ERRCODE='42501';
 END IF;
 IF p_desde IS NULL OR p_hasta IS NULL OR p_hasta<p_desde THEN
  RAISE EXCEPTION 'Indica un rango de fechas válido' USING ERRCODE='22023';
 END IF;
 IF p_contrato IS NOT NULL AND NOT(p_contrato=ANY(public.crm_portal_ids('CONTRATO'))) THEN
  RAISE EXCEPTION 'Contrato no disponible en tu portal' USING ERRCODE='42501';
 END IF;
 IF p_campana IS NOT NULL AND NOT(p_campana=ANY(public.crm_portal_ids('CAMPANA'))) THEN
  RAISE EXCEPTION 'Campaña no disponible en tu portal' USING ERRCODE='42501';
 END IF;

 WITH contratos AS (
  SELECT c.id,c.nombre,c.servicio,c.estado,c.desde,c.hasta,c.moneda,
    c.honorarios,c.produccion,c.presupuesto,c.alcance
  FROM public.crm_acuerdo c WHERE c.tipo='CONTRATO'
 ), anuncios AS (
  SELECT a.id,a.contrato_id,a.campana_id,a.nombre,a.codigo,
    p.plataforma,p.moneda,c.nombre campana
  FROM public.crm_anuncio a
  JOIN public.crm_cuenta_publicitaria p ON p.id=a.cuenta_id
  JOIN public.campana c ON c.id_campana=a.campana_id
 ), vinculos AS (
  SELECT a.id anuncio_id,a.contrato_id FROM anuncios a JOIN contratos c ON c.id=a.contrato_id
  UNION
  SELECT v.anuncio_id,v.contrato_id FROM public.crm_contrato_anuncio v
  JOIN anuncios a ON a.id=v.anuncio_id JOIN contratos c ON c.id=v.contrato_id
 ), registros AS (
  SELECT m.fecha,m.pais,a.plataforma,a.moneda,a.campana_id,a.campana,
    elegido.id contrato_id,elegido.nombre contrato,
    sum(m.impresiones) impresiones,sum(m.clics) clics,
    sum(m.conversiones) conversiones,sum(m.inversion) inversion,
    sum(m.ingresos) ingresos_estimados
  FROM public.crm_anuncio_metrica m JOIN anuncios a ON a.id=m.anuncio_id
  JOIN LATERAL (
   SELECT c.id,c.nombre FROM vinculos v JOIN contratos c ON c.id=v.contrato_id
   WHERE v.anuncio_id=a.id AND (p_contrato IS NULL OR c.id=p_contrato)
   ORDER BY (c.id=a.contrato_id) DESC,c.id LIMIT 1
  ) elegido ON true
  WHERE m.fecha BETWEEN p_desde AND p_hasta
    AND (p_campana IS NULL OR a.campana_id=p_campana)
    AND (nullif(p_plataforma,'') IS NULL OR a.plataforma=p_plataforma)
    AND (nullif(p_pais,'') IS NULL OR m.pais=p_pais)
    AND (nullif(p_moneda,'') IS NULL OR a.moneda=p_moneda)
    AND (coalesce(p_busqueda,'')='' OR strpos(lower(concat_ws(' ',a.campana,a.nombre,a.codigo,a.plataforma,m.pais,elegido.nombre)),lower(p_busqueda))>0
      OR EXISTS(SELECT 1 FROM vinculos v JOIN contratos c ON c.id=v.contrato_id
        WHERE v.anuncio_id=a.id AND (p_contrato IS NULL OR c.id=p_contrato)
          AND strpos(lower(c.nombre),lower(p_busqueda))>0))
  GROUP BY m.fecha,m.pais,a.plataforma,a.moneda,a.campana_id,a.campana,elegido.id,elegido.nombre
 )
 SELECT jsonb_build_object(
  'empresa',(SELECT jsonb_build_object('id',e.id,'nombre',e.nombre) FROM public.crm_cuenta e),
  'contratos',coalesce((SELECT jsonb_agg(to_jsonb(c) ORDER BY c.id DESC) FROM contratos c),'[]'::jsonb),
  'campanas',coalesce((SELECT jsonb_agg(jsonb_build_object('id',c.id_campana,'nombre',c.nombre,'contratos',
    coalesce((SELECT jsonb_agg(DISTINCT v.contrato_id) FROM (
      SELECT v.contrato_id FROM vinculos v JOIN anuncios a ON a.id=v.anuncio_id WHERE a.campana_id=c.id_campana
      UNION SELECT ac.contrato_id FROM public.crm_acuerdo_campana ac WHERE ac.campana_id=c.id_campana
    )v),'[]'::jsonb)) ORDER BY c.nombre)
    FROM public.campana c),'[]'::jsonb),
  'plataformas',coalesce((SELECT jsonb_agg(x.plataforma ORDER BY x.plataforma) FROM(SELECT DISTINCT plataforma FROM anuncios)x),'[]'::jsonb),
  'monedas',coalesce((SELECT jsonb_agg(x.moneda ORDER BY x.moneda) FROM(SELECT DISTINCT moneda FROM anuncios)x),'[]'::jsonb),
  'paises',coalesce((SELECT jsonb_agg(x.pais ORDER BY x.pais) FROM(SELECT DISTINCT m.pais FROM public.crm_anuncio_metrica m JOIN anuncios a ON a.id=m.anuncio_id)x),'[]'::jsonb),
  'registros',coalesce((SELECT jsonb_agg(to_jsonb(r) ORDER BY r.fecha,r.pais,r.campana_id,r.plataforma,r.moneda) FROM registros r),'[]'::jsonb),
  'desde',p_desde,'hasta',p_hasta,'simulado',true,'proveedor','MOCKAROO',
  'alcanceDisponible',false,'retornoVerificado',false
 ) INTO resultado;
 RETURN resultado;
END;
$$;
REVOKE ALL ON FUNCTION public.crm_portal_estadisticas(date,date,bigint,bigint,text,text,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.crm_portal_estadisticas(date,date,bigint,bigint,text,text,text,text) TO authenticated;
COMMENT ON FUNCTION public.crm_portal_estadisticas(date,date,bigint,bigint,text,text,text,text)
 IS 'Analítica del portal sobre métricas simuladas de Mockaroo, con RLS y anuncios compartidos deduplicados.';
NOTIFY pgrst, 'reload schema';
COMMIT;
