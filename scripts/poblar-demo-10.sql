-- Ejecutar dentro de UNA transacción, después de metricas-demo-mockaroo.sql.
-- Solo añade registros DEMO10. No modifica auth, roles, permisos ni proveedores.
SELECT pg_advisory_xact_lock(2026112410);
SET LOCAL TIME ZONE 'America/Bogota';
CREATE TEMP TABLE demo_ids(modulo text,lote int,id bigint,PRIMARY KEY(modulo,lote)) ON COMMIT DROP;
CREATE TEMP TABLE demo_resumen(modulo text PRIMARY KEY,registros bigint) ON COMMIT DROP;

-- Las claves estables hacen repetible la carga; nunca se actualizan filas previas.
CREATE OR REPLACE FUNCTION pg_temp.demo_insertar(tabla text,pk text,clave text,valor text,datos jsonb)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE resultado bigint; cantidad bigint; columnas text; campos text;
BEGIN
 EXECUTE format('SELECT min(%I),count(*) FROM public.%I WHERE %I::text=$1',pk,tabla,clave)
  INTO resultado,cantidad USING valor;
 IF cantidad>1 THEN RAISE EXCEPTION 'Clave DEMO duplicada en %; no se hizo una carga parcial',tabla; END IF;
 IF resultado IS NOT NULL THEN RETURN resultado; END IF;
 SELECT string_agg(format('%I',k),',' ORDER BY k),string_agg(format('r.%I',k),',' ORDER BY k)
  INTO columnas,campos FROM jsonb_object_keys(datos) k;
 EXECUTE format('INSERT INTO public.%I(%s) SELECT %s FROM jsonb_populate_record(NULL::public.%I,$1) r RETURNING %I',tabla,columnas,campos,tabla,pk)
  INTO resultado USING datos;
 RETURN resultado;
END $$;

DO $$
DECLARE
 i int; n text; autor bigint; rol_cliente bigint; rol_equipo bigint;
 cliente bigint; empresa bigint; canal bigint; campana bigint; audiencia bigint; interes bigint;
 cotizacion bigint; contrato bigint; cuenta_ad bigint; contacto bigint; oportunidad bigint; segmento bigint;
 contenido bigint; anuncio bigint; demo bigint; trabajador bigint; portal bigint; etapa bigint;
 tipo text; prestacion text; plataforma text; empresa_nombre text; persona_nombre text; nuevo boolean;
 empresas text[]:=ARRAY['Andina Studio','Nova Retail','Horizonte Digital','Café Aurora','Eco Hogar','Nexo Tecnología','Brisa Viajes','Origen Natural','Impulso Educación','Luna Moda'];
 personas text[]:=ARRAY['Camila Torres','Diego Mendoza','Valentina Rojas','Mateo Campos','Lucía Pérez','Sebastián Vega','Daniela Castro','Gabriel Flores','Mariana Ruiz','Nicolás Medina'];
 paises text[]:=ARRAY['PE','CO','MX','ES','US','BR','JP','ZA','AU','PE'];
 canales text[]:=ARRAY['Facebook','Instagram','TikTok','YouTube','Búsqueda','Email','Display','LinkedIn','Contenido web','Podcast'];
BEGIN
 IF EXISTS(SELECT 1 FROM public.crm_empresa_migracion m WHERE m.tipo='lote_marketing_20261124' AND m.registro_id=0) THEN
  INSERT INTO demo_ids SELECT r.* FROM public.crm_empresa_migracion m CROSS JOIN LATERAL jsonb_to_recordset(m.datos->'registros') r(modulo text,lote int,id bigint) WHERE m.tipo='lote_marketing_20261124' AND m.registro_id=0;
  RETURN;
 END IF;
 IF to_regclass('pg_temp.demo_estadisticas') IS NULL THEN RAISE EXCEPTION 'Carga primero las estadísticas generadas por Mockaroo'; END IF;
 IF (SELECT count(*) FROM demo_estadisticas)<>900 THEN RAISE EXCEPTION 'Se requieren las 900 filas completas de Mockaroo'; END IF;
 SELECT min(u.id_usuario) INTO autor FROM public.usuario u JOIN public.rol r USING(id_rol)
  WHERE upper(u.estado)='ACTIVO' AND lower(r.nombre_rol) LIKE '%admin%';
 SELECT min(id_rol) INTO rol_cliente FROM public.rol WHERE lower(nombre_rol)='cliente';
 IF autor IS NULL OR rol_cliente IS NULL THEN RAISE EXCEPTION 'Se requiere un administrador activo y el rol Cliente existente'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.crm_servicio WHERE codigo='INTEGRAL') THEN RAISE EXCEPTION 'Falta el catálogo de servicios existente'; END IF;
 -- Recupera el catálogo mínimo solo si falta; conserva las etapas configuradas.
 IF NOT EXISTS(SELECT 1 FROM public.crm_etapa) THEN
  INSERT INTO public.crm_etapa(nombre,orden,tipo) VALUES('Nuevo',0,'ABIERTA'),('Calificado',1,'ABIERTA'),('Propuesta',2,'ABIERTA'),('Ganado',3,'GANADA'),('Perdido',4,'PERDIDA');
 ELSIF NOT EXISTS(SELECT 1 FROM public.crm_etapa e WHERE e.tipo='ABIERTA') THEN
  INSERT INTO public.crm_etapa(nombre,orden,tipo)
   SELECT 'DEMO10 · Etapa inicial',coalesce(max(orden),-1)+1,'ABIERTA' FROM public.crm_etapa;
 END IF;
 FOR i IN 1..10 LOOP
  n:=lpad(i::text,2,'0');
  empresa_nombre:='DEMO10 · '||empresas[i]; persona_nombre:='DEMO10 · '||personas[i];
  plataforma:=(ARRAY['FACEBOOK','INSTAGRAM','TIKTOK','YOUTUBE'])[1+(i-1)%4];
  SELECT min(id_rol) INTO rol_equipo FROM public.rol WHERE
   CASE WHEN i%2=0 THEN lower(nombre_rol) LIKE '%analista%' ELSE lower(nombre_rol) LIKE '%manager%' OR lower(nombre_rol) LIKE '%lider%' END;
  IF rol_equipo IS NULL THEN RAISE EXCEPTION 'Faltan los perfiles de equipo existentes'; END IF;
  -- Perfiles sin identidad Supabase ni contraseña: no pueden iniciar sesión.
  trabajador:=pg_temp.demo_insertar('usuario','id_usuario','correo','demo10.equipo.'||n||'@example.invalid',jsonb_build_object(
   'nombres',persona_nombre,'correo','demo10.equipo.'||n||'@example.invalid','estado','INACTIVO','id_rol',rol_equipo,'debe_cambiar_password',true));
  portal:=pg_temp.demo_insertar('usuario','id_usuario','correo','demo10.portal.'||n||'@example.invalid',jsonb_build_object(
   'nombres','DEMO10 · Cliente '||n,'correo','demo10.portal.'||n||'@example.invalid','estado','INACTIVO','id_rol',rol_cliente,'debe_cambiar_password',true));
  INSERT INTO demo_ids VALUES('Trabajadores de prueba',i,trabajador),('Perfiles de cliente de prueba',i,portal);
  nuevo:=NOT EXISTS(SELECT 1 FROM public.cliente WHERE correo='demo10.empresa.'||n||'@example.invalid');
  cliente:=pg_temp.demo_insertar('cliente','id_cliente','correo','demo10.empresa.'||n||'@example.invalid',jsonb_build_object(
   'nombre',empresa_nombre,'correo','demo10.empresa.'||n||'@example.invalid','estado','ACTIVO'));
  SELECT id INTO empresa FROM public.crm_cuenta WHERE cliente_id=cliente AND fusionada_en IS NULL;
  IF empresa IS NULL THEN RAISE EXCEPTION 'La empresa % no tiene ficha CRM; revisa la migración v9',n; END IF;
  IF nuevo THEN UPDATE public.crm_cuenta SET sector=(ARRAY['Diseño','Comercio','Servicios','Alimentación','Hogar','Tecnología','Turismo','Bienestar','Educación','Moda'])[i],sitio_web='https://empresa-'||n||'.example.invalid' WHERE id=empresa; END IF;
  INSERT INTO demo_ids VALUES('Empresas',i,empresa);
  canal:=pg_temp.demo_insertar('canal_difusion','id_canal','nombre_canal','DEMO10 · '||canales[i],jsonb_build_object(
   'nombre_canal','DEMO10 · '||canales[i],'tipo_canal',CASE WHEN i=5 THEN 'BUSQUEDA' WHEN i=6 THEN 'EMAIL' WHEN i=7 THEN 'DISPLAY' WHEN i IN(3,4) THEN 'VIDEO' WHEN i IN(9,10) THEN 'OTRO' ELSE 'REDES_SOCIALES' END,'estado','ACTIVO'));
  INSERT INTO demo_ids VALUES('Canales de difusión',i,canal);
  campana:=pg_temp.demo_insertar('campana','id_campana','nombre','DEMO10 · Campaña '||n||' · '||empresas[i],jsonb_build_object(
   'nombre','DEMO10 · Campaña '||n||' · '||empresas[i],'id_cliente',cliente,'id_canal',canal,'objetivo','Demostración de alcance y conversiones publicitarias simuladas. No acredita ventas.',
   'presupuesto_asignado',20000+i*500,'fecha_inicio',(SELECT fecha FROM demo_contexto)-29,'fecha_fin',(SELECT fecha FROM demo_contexto)+30,'estado','ACTIVA','id_usuario_creador',autor));
  INSERT INTO demo_ids VALUES('Campañas',i,campana);
  audiencia:=pg_temp.demo_insertar('segmento_audiencia','id_segmento','nombre','DEMO10 · Audiencia '||n,jsonb_build_object(
   'nombre','DEMO10 · Audiencia '||n,'descripcion','Audiencia ficticia para pruebas de marketing','criterios','Interés temático de demostración; no contiene personas reales',
   'edad_min',18,'edad_max',60,'ubicacion_geografica',paises[i],'nivel_socioeconomico','B','estado','ACTIVO','id_usuario_creador',autor));
  interes:=pg_temp.demo_insertar('interes','id_interes','nombre_interes','DEMO10 · Interés '||n,jsonb_build_object('nombre_interes','DEMO10 · Interés '||n));
  INSERT INTO public.segmento_interes(id_segmento,id_interes) VALUES(audiencia,interes) ON CONFLICT DO NOTHING;
  INSERT INTO public.campana_audiencia(id_campana,id_segmento,id_usuario_vinculo) VALUES(campana,audiencia,autor) ON CONFLICT DO NOTHING;
  INSERT INTO demo_ids VALUES('Audiencias',i,audiencia);
  cotizacion:=pg_temp.demo_insertar('crm_acuerdo','id','nombre','DEMO10 · Cotización '||n,jsonb_build_object(
   'tipo','COTIZACION','empresa_id',empresa,'nombre','DEMO10 · Cotización '||n,'servicio','INTEGRAL','modalidad','PAGADA','desde',(SELECT fecha FROM demo_contexto)-29,'hasta',(SELECT fecha FROM demo_contexto)+30,
   'moneda','PEN','honorarios',800+i*100,'presupuesto',20000+i*500,'produccion',300+i*20,'responsable_id',autor,'alcance','Propuesta ficticia: contenido, difusión simulada, análisis e informes.',
   'plataformas',plataforma,'condiciones','Datos simulados. Sin publicación ni compromisos comerciales reales.','frecuencia','Semanal','estado','APROBADO'));
  contrato:=pg_temp.demo_insertar('crm_acuerdo','id','nombre','DEMO10 · Contrato '||n,jsonb_build_object(
   'tipo','CONTRATO','empresa_id',empresa,'origen_id',cotizacion,'nombre','DEMO10 · Contrato '||n,'servicio','INTEGRAL','modalidad','PAGADA','desde',(SELECT fecha FROM demo_contexto)-29,'hasta',(SELECT fecha FROM demo_contexto)+30,
   'moneda','PEN','honorarios',800+i*100,'presupuesto',20000+i*500,'produccion',300+i*20,'responsable_id',autor,'alcance','Contrato de demostración: producción, edición, difusión simulada, análisis e informes.',
   'plataformas',plataforma,'condiciones','Registro de prueba; no acredita contratación real ni autoriza publicar anuncios.','frecuencia','Semanal','estado','ACTIVO'));
  INSERT INTO demo_ids VALUES('Cotizaciones',i,cotizacion),('Contratos',i,contrato);
  INSERT INTO public.crm_acuerdo_campana(contrato_id,campana_id) VALUES(contrato,campana) ON CONFLICT DO NOTHING;
  FOREACH prestacion IN ARRAY ARRAY['PRODUCCION','EDICION','PUBLICACION','ANALISIS','INFORMES'] LOOP
   INSERT INTO public.crm_alcance_servicio(contrato_id,prestacion,cantidad) VALUES(contrato,prestacion,20),(cotizacion,prestacion,20) ON CONFLICT DO NOTHING;
  END LOOP;
  cuenta_ad:=pg_temp.demo_insertar('crm_cuenta_publicitaria','id','codigo','DEMO10-CTA-'||n,jsonb_build_object(
   'empresa_id',empresa,'nombre','DEMO10 · Cuenta '||n,'codigo','DEMO10-CTA-'||n,'plataforma',plataforma,'moneda','PEN'));
  INSERT INTO demo_ids VALUES('Cuentas publicitarias simuladas',i,cuenta_ad);
  contacto:=pg_temp.demo_insertar('crm_contacto','id','correo','demo10.contacto.'||n||'@example.invalid',jsonb_build_object(
   'nombre',persona_nombre,'correo','demo10.contacto.'||n||'@example.invalid','estado',(ARRAY['NUEVO','CONTACTADO','CALIFICADO'])[1+(i-1)%3],
   'origen','DEMO10','etiquetas','DEMO10-'||n,'cuenta_id',empresa,'campana_id',campana,'responsable_id',autor));
  INSERT INTO demo_ids VALUES('Contactos / Leads',i,contacto);
  SELECT e.id INTO etapa FROM public.crm_etapa e WHERE e.tipo='ABIERTA' ORDER BY e.orden LIMIT 1 OFFSET ((i-1)%3);
  IF etapa IS NULL THEN SELECT min(e.id) INTO etapa FROM public.crm_etapa e WHERE e.tipo='ABIERTA'; END IF;
  oportunidad:=pg_temp.demo_insertar('crm_oportunidad','id','nombre','DEMO10 · Oportunidad '||n,jsonb_build_object(
   'nombre','DEMO10 · Oportunidad '||n,'contacto_id',contacto,'etapa_id',etapa,'responsable_id',autor,'valor',1000+i*300,'moneda','PEN','probabilidad',20+i*5,'fecha_cierre',(SELECT fecha FROM demo_contexto)+15+i));
  INSERT INTO demo_ids VALUES('Pipeline',i,oportunidad);
  INSERT INTO demo_ids SELECT 'Actividades',i,pg_temp.demo_insertar('crm_actividad','id','nombre','DEMO10 · Seguimiento '||n,jsonb_build_object(
   'nombre','DEMO10 · Seguimiento '||n,'tipo',(ARRAY['LLAMADA','REUNION','SEGUIMIENTO','RECORDATORIO'])[1+(i-1)%4],'estado','PENDIENTE',
   'fecha',((SELECT fecha FROM demo_contexto)+i)::timestamp+interval '10 hours','responsable_id',autor,'oportunidad_id',oportunidad,'descripcion','Actividad de prueba. No contacta a personas reales.'));
  segmento:=pg_temp.demo_insertar('crm_segmento','id','nombre','DEMO10 · Segmento '||n,jsonb_build_object('nombre','DEMO10 · Segmento '||n,'etiqueta','DEMO10-'||n,'origen','DEMO10'));
  INSERT INTO demo_ids VALUES('Segmentos dinámicos',i,segmento);
  INSERT INTO demo_ids SELECT 'Reglas de puntuación',i,pg_temp.demo_insertar('crm_regla_score','id','nombre','DEMO10 · Regla '||n,jsonb_build_object(
   'nombre','DEMO10 · Regla '||n,'campo','ETIQUETA','valor','DEMO10-'||n,'puntos',10+i));
  INSERT INTO demo_ids SELECT 'Metas y alertas',i,pg_temp.demo_insertar('crm_meta','id','nombre','DEMO10 · Meta '||n,jsonb_build_object(
   'nombre','DEMO10 · Meta '||n,'campana_id',campana,'desde',(SELECT fecha FROM demo_contexto)-9,'hasta',(SELECT fecha FROM demo_contexto)+20,'leads',5+i,'ingresos',0,'moneda','PEN'));
  contenido:=pg_temp.demo_insertar('crm_contenido','id','nombre','DEMO10 · Contenido '||n,jsonb_build_object('nombre','DEMO10 · Contenido '||n,
   'campana_id',campana,'responsable_id',autor,'estado','BORRADOR','texto','Propuesta creativa de demostración para '||empresas[i]||'. Datos simulados: contenido sin publicación real.'));
  INSERT INTO demo_ids VALUES('Contenido y aprobaciones',i,contenido);
  anuncio:=pg_temp.demo_insertar('crm_anuncio','id','codigo','DEMO10-AD-'||n,jsonb_build_object('contrato_id',contrato,'cuenta_id',cuenta_ad,'campana_id',campana,
   'codigo','DEMO10-AD-'||n,'nombre','DEMO10 · Anuncio '||n,'formato','TEXTO','estado','BORRADOR','contenido_id',contenido,'fecha',(SELECT fecha FROM demo_contexto)+i,'requiere_aprobacion_cliente',false));
  INSERT INTO demo_ids VALUES('Anuncios / calendario editorial',i,anuncio);
  INSERT INTO public.crm_contrato_anuncio(contrato_id,anuncio_id,origen) VALUES(contrato,anuncio,'CREADO') ON CONFLICT DO NOTHING;
  FOREACH tipo IN ARRAY ARRAY['ENTREGABLE','RECOMENDACION','AJUSTE','INFORME'] LOOP
   INSERT INTO demo_ids SELECT tipo,i,pg_temp.demo_insertar('crm_trabajo_servicio','id','nombre','DEMO10 · '||tipo||' '||n,jsonb_build_object(
    'contrato_id',contrato,'tipo',tipo,'anuncio_id',anuncio,'nombre','DEMO10 · '||tipo||' '||n,'descripcion',
    CASE tipo WHEN 'ENTREGABLE' THEN 'Propuesta creativa y ficha de contenido de prueba.' WHEN 'RECOMENDACION' THEN 'Revisar el CTR por país con las estadísticas simuladas.' WHEN 'AJUSTE' THEN 'Propuesta de ajuste pendiente; no ejecuta cambios en plataformas.' ELSE 'Informe de prueba sobre las estadísticas simuladas de Mockaroo.' END,
    'fecha',(SELECT fecha FROM demo_contexto)+i,'cantidad',1,'importe',0,'estado','PENDIENTE','prestacion',CASE WHEN tipo='ENTREGABLE' THEN 'PRODUCCION' WHEN tipo='INFORME' THEN 'INFORMES' ELSE 'ANALISIS' END));
  END LOOP;
  INSERT INTO demo_ids SELECT 'Automatizaciones inactivas',i,pg_temp.demo_insertar('crm_automatizacion','id','nombre','DEMO10 · Flujo '||n,jsonb_build_object(
   'nombre','DEMO10 · Flujo '||n,'segmento_id',segmento,'accion','CREAR_TAREA','asunto','DEMO10 · Seguimiento interno '||n,'mensaje','Flujo de prueba sin ejecución automática.','activa',false,'autor_id',autor));
  INSERT INTO demo_ids SELECT 'Campos personalizados',i,pg_temp.demo_insertar('crm_campo','id','codigo','demo10_campo_'||n,jsonb_build_object(
   'codigo','demo10_campo_'||n,'nombre','DEMO10 · Campo '||n,'tipo','TEXTO','activo',true));
  INSERT INTO public.crm_campo_valor(contacto_id,campo_id,valor)
   SELECT contacto,id,to_jsonb(('Valor de prueba '||n)::text) FROM demo_ids WHERE modulo='Campos personalizados' AND lote=i ON CONFLICT DO NOTHING;
  INSERT INTO public.crm_origen(contacto_id,campana_id,canal,utm_source,utm_medium,utm_campaign,idempotencia)
   VALUES(contacto,campana,'DEMO10','demo10','simulado','demo10-'||n,('d3100000-0000-4000-8000-'||lpad(i::text,12,'0'))::uuid) ON CONFLICT DO NOTHING;
  INSERT INTO public.crm_formulario(id,campana_id,nombre,responsable_id,activo,secreto_hash)
   VALUES(('d3110000-0000-4000-8000-'||lpad(i::text,12,'0'))::uuid,campana,'DEMO10 · Formulario '||n,autor,false,repeat('0',64)) ON CONFLICT DO NOTHING;
  INSERT INTO public.crm_portal_acceso(usuario_id,empresa_id,activo,otorgado_por) VALUES(portal,empresa,false,autor) ON CONFLICT DO NOTHING;
  INSERT INTO demo_ids VALUES('Accesos al portal desactivados',i,portal);
  INSERT INTO demo_ids SELECT 'Notas',i,pg_temp.demo_insertar('crm_nota','id','texto','DEMO10 · Nota '||n||': ficha de empresa ficticia.',jsonb_build_object(
   'cuenta_id',empresa,'texto','DEMO10 · Nota '||n||': ficha de empresa ficticia.','autor_id',autor));
  INSERT INTO demo_ids SELECT 'Eventos del CRM',i,pg_temp.demo_insertar('crm_evento','id','detalle','DEMO10 · Alta de demostración '||n,jsonb_build_object(
   'cuenta_id',empresa,'tipo','DEMO10','detalle','DEMO10 · Alta de demostración '||n,'autor_id',autor));
  INSERT INTO demo_ids SELECT 'Auditoría de la carga',i,pg_temp.demo_insertar('crm_auditoria','id','accion','DEMO10_CARGA_'||n,jsonb_build_object(
   'recurso','cuentas','registro_id',empresa,'accion','DEMO10_CARGA_'||n,'autor_id',autor));
  INSERT INTO public.crm_inversion(campana_id,fecha,moneda,concepto,importe,idempotencia,creado_por)
   VALUES(campana,(SELECT fecha FROM demo_contexto),'PEN','DEMO10 · Costo adicional simulado '||n,25+i,('d3120000-0000-4000-8000-'||lpad(i::text,12,'0'))::uuid,autor) ON CONFLICT DO NOTHING;
  demo:=pg_temp.demo_insertar('crm_publicidad_demo','id','codigo','DEMO10-SIM-'||n,jsonb_build_object('campana_id',campana,'codigo','DEMO10-SIM-'||n,
   'plataforma',CASE WHEN plataforma IN('FACEBOOK','INSTAGRAM') THEN 'META' ELSE plataforma END,'moneda','PEN','creado_por',autor,'sincronizado_en',now()));
  INSERT INTO demo_ids VALUES('Rendimiento publicitario DEMO',i,demo);
 END LOOP;
END $$;

-- Mismas estadísticas Mockaroo, en los modelos de lectura ya existentes.
INSERT INTO public.crm_anuncio_metrica(anuncio_id,fecha,pais,impresiones,clics,conversiones,inversion,ingresos)
 SELECT d.id,(SELECT fecha FROM demo_contexto)-9+e.dia,e.pais,e.impresiones,e.clics,e.conversiones,e.inversion,e.ingresos
 FROM demo_estadisticas e JOIN demo_ids d ON d.lote=e.lote AND d.modulo='Anuncios / calendario editorial' ON CONFLICT DO NOTHING;
INSERT INTO public.crm_publicidad_demo_metrica(demo_id,fecha,pais,impresiones,clics,conversiones,inversion,ingresos)
 SELECT d.id,(SELECT fecha FROM demo_contexto)-9+e.dia,e.pais,e.impresiones,e.clics,e.conversiones,e.inversion,e.ingresos
 FROM demo_estadisticas e JOIN demo_ids d ON d.lote=e.lote AND d.modulo='Rendimiento publicitario DEMO' ON CONFLICT DO NOTHING;
INSERT INTO public.metrica_operativa(id_campana,fecha,tipo_metrica,impresiones,clics,alcance,interacciones,conversiones,inversion,ingresos,id_usuario_registro)
 SELECT d.id,(SELECT fecha FROM demo_contexto)-9+e.dia,'DIARIA',sum(e.impresiones),sum(e.clics),0,0,sum(e.conversiones),sum(e.inversion),sum(e.ingresos),
  (SELECT min(u.id_usuario) FROM public.usuario u JOIN public.rol r USING(id_rol) WHERE upper(u.estado)='ACTIVO' AND lower(r.nombre_rol) LIKE '%admin%')
 FROM demo_estadisticas e JOIN demo_ids d ON d.lote=e.lote AND d.modulo='Campañas' GROUP BY d.id,e.dia ON CONFLICT DO NOTHING;

INSERT INTO public.marketing_ubicacion(pais,codigo_pais,ciudad,latitud,longitud) VALUES
 ('Perú','PE','',-9.19,-75.02),('Colombia','CO','',4.57,-74.30),('México','MX','',23.63,-102.55),
 ('España','ES','',40.46,-3.75),('Estados Unidos','US','',37.09,-95.71),('Brasil','BR','',-14.24,-51.93),
 ('Japón','JP','',36.20,138.25),('Sudáfrica','ZA','',-30.56,22.94),('Australia','AU','',-25.27,133.78)
 ON CONFLICT DO NOTHING;
INSERT INTO public.marketing_resultado_geografico(metrica_id,ubicacion_id,impresiones,clics,conversiones,inversion,ingresos)
 SELECT m.id_metrica,u.id,e.impresiones,e.clics,e.conversiones,e.inversion,e.ingresos
 FROM demo_estadisticas e JOIN demo_ids d ON d.lote=e.lote AND d.modulo='Campañas'
 JOIN public.metrica_operativa m ON m.id_campana=d.id AND m.tipo_metrica='DIARIA' AND m.fecha=(SELECT fecha FROM demo_contexto)-9+e.dia
 JOIN public.marketing_ubicacion u ON u.codigo_pais=e.pais AND u.ciudad=''
 ON CONFLICT DO NOTHING;
INSERT INTO public.marketing_cobertura(campana_id,ubicacion_id)
 SELECT DISTINCT m.id_campana,g.ubicacion_id FROM public.marketing_resultado_geografico g
 JOIN public.metrica_operativa m ON m.id_metrica=g.metrica_id JOIN demo_ids d ON d.id=m.id_campana AND d.modulo='Campañas' ON CONFLICT DO NOTHING;

INSERT INTO public.indicador_kpi(id_campana,periodo_inicio,periodo_fin,ctr,cpc,cpm,roi)
 SELECT d.id,(SELECT fecha FROM demo_contexto)-9,(SELECT fecha FROM demo_contexto),round(sum(m.clics)*100.0/nullif(sum(m.impresiones),0),4),round(sum(m.inversion)/nullif(sum(m.clics),0),4),
 round(sum(m.inversion)*1000/nullif(sum(m.impresiones),0),4),NULL
 FROM demo_ids d JOIN public.metrica_operativa m ON m.id_campana=d.id AND m.fecha BETWEEN (SELECT fecha FROM demo_contexto)-9 AND (SELECT fecha FROM demo_contexto)
 WHERE d.modulo='Campañas' GROUP BY d.id ON CONFLICT DO NOTHING;
-- ROI permanece sin calcular: las conversiones e ingresos de Mockaroo no son ventas confirmadas.
INSERT INTO public.reporte(tipo_reporte,fecha_desde,fecha_hasta,formato_exportacion,id_usuario,id_campana)
 SELECT 'CAMPANA',(SELECT fecha FROM demo_contexto)-9,(SELECT fecha FROM demo_contexto),'CSV',c.id_usuario_creador,c.id_campana
 FROM demo_ids d JOIN public.campana c ON c.id_campana=d.id WHERE d.modulo='Campañas' AND NOT EXISTS(
  SELECT 1 FROM public.reporte r WHERE r.id_campana=c.id_campana AND r.tipo_reporte='CAMPANA');

INSERT INTO demo_resumen SELECT modulo,count(*) FROM demo_ids GROUP BY modulo;
INSERT INTO demo_resumen VALUES
 ('Usuarios de prueba (equipo + clientes)',(SELECT count(*) FROM public.usuario u JOIN demo_ids d ON d.id=u.id_usuario AND d.modulo IN('Trabajadores de prueba','Perfiles de cliente de prueba'))),
 ('Métricas diarias / dashboard',(SELECT count(*) FROM public.metrica_operativa m JOIN demo_ids d ON d.id=m.id_campana AND d.modulo='Campañas')),
 ('Estadísticas por anuncio y país',(SELECT count(*) FROM public.crm_anuncio_metrica m JOIN demo_ids d ON d.id=m.anuncio_id AND d.modulo='Anuncios / calendario editorial')),
 ('Estadísticas publicitarias DEMO por país',(SELECT count(*) FROM public.crm_publicidad_demo_metrica m JOIN demo_ids d ON d.id=m.demo_id AND d.modulo='Rendimiento publicitario DEMO')),
 ('Resultados geográficos del mapa',(SELECT count(*) FROM public.marketing_resultado_geografico g JOIN public.metrica_operativa m ON m.id_metrica=g.metrica_id JOIN demo_ids d ON d.id=m.id_campana AND d.modulo='Campañas')),
 ('Indicadores / análisis comparativo',(SELECT count(*) FROM public.indicador_kpi k JOIN demo_ids d ON d.id=k.id_campana AND d.modulo='Campañas')),
 ('Reportes por campaña',(SELECT count(*) FROM public.reporte r JOIN demo_ids d ON d.id=r.id_campana AND d.modulo='Campañas')),
 ('Atribución de contactos',(SELECT count(*) FROM public.crm_origen o JOIN demo_ids d ON d.id=o.contacto_id AND d.modulo='Contactos / Leads')),
 ('Gastos adicionales simulados',(SELECT count(*) FROM public.crm_inversion v JOIN demo_ids d ON d.id=v.campana_id AND d.modulo='Campañas'));

DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM demo_resumen WHERE modulo NOT IN ('Usuarios de prueba (equipo + clientes)','Métricas diarias / dashboard','Estadísticas por anuncio y país','Estadísticas publicitarias DEMO por país','Resultados geográficos del mapa') AND registros<>10) THEN
  RAISE EXCEPTION 'El resumen no contiene los 10 registros esperados por módulo';
 END IF;
 IF EXISTS(SELECT 1 FROM public.usuario u JOIN demo_ids d ON d.id=u.id_usuario AND d.modulo IN('Trabajadores de prueba','Perfiles de cliente de prueba') WHERE u.id_supabase IS NOT NULL OR u.password_hash IS NOT NULL OR u.estado<>'INACTIVO') THEN
  RAISE EXCEPTION 'Se detectó un perfil DEMO con acceso de autenticación; se revierte toda la carga';
 END IF;
 IF EXISTS(SELECT 1 FROM public.crm_portal_acceso p JOIN demo_ids d ON d.id=p.usuario_id AND d.modulo='Accesos al portal desactivados' WHERE p.activo) THEN
  RAISE EXCEPTION 'Los accesos de ejemplo deben permanecer desactivados';
 END IF;
 IF EXISTS(SELECT 1 FROM public.crm_automatizacion a JOIN demo_ids d ON d.id=a.id AND d.modulo='Automatizaciones inactivas' WHERE a.activa) THEN
  RAISE EXCEPTION 'Las automatizaciones DEMO deben permanecer inactivas';
 END IF;
 IF EXISTS(SELECT 1 FROM public.metrica_operativa m JOIN demo_ids d ON d.id=m.id_campana AND d.modulo='Campañas'
  JOIN LATERAL(SELECT sum(g.impresiones) impresiones,sum(g.clics) clics,sum(g.conversiones) conversiones,sum(g.inversion) inversion,sum(g.ingresos) ingresos FROM public.marketing_resultado_geografico g WHERE g.metrica_id=m.id_metrica) g ON true
  WHERE (m.impresiones,m.clics,m.conversiones,m.inversion,m.ingresos) IS DISTINCT FROM (g.impresiones,g.clics,g.conversiones,g.inversion,g.ingresos)) THEN
  RAISE EXCEPTION 'Los desgloses del mapa no coinciden con las métricas; se revierte la carga';
 END IF;
END $$;

