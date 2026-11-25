-- Cambia solo la presentación del lote creado el 24/11/2026. No borra registros.
-- La procedencia sintética queda en el inventario interno existente.
SELECT pg_advisory_xact_lock(2026112410);
CREATE TEMP TABLE IF NOT EXISTS demo_ids(modulo text,lote int,id bigint,PRIMARY KEY(modulo,lote)) ON COMMIT DROP;
CREATE TEMP TABLE normalizacion_resumen(modulo text,registros bigint) ON COMMIT DROP;
DO $$
DECLARE spec record; cantidad bigint;
BEGIN
 IF NOT EXISTS(SELECT 1 FROM demo_ids) THEN
  IF EXISTS(SELECT 1 FROM public.crm_empresa_migracion WHERE tipo='lote_marketing_20261124' AND registro_id=0) THEN
   INSERT INTO demo_ids SELECT r.* FROM public.crm_empresa_migracion m CROSS JOIN LATERAL jsonb_to_recordset(m.datos->'registros') r(modulo text,lote int,id bigint) WHERE m.tipo='lote_marketing_20261124' AND m.registro_id=0;
  ELSE
   FOR spec IN SELECT * FROM (VALUES
    ('Trabajadores de prueba','usuario','id_usuario','correo','^demo10[.]equipo[.]([0-9]{2})@example[.]invalid$'),
    ('Perfiles de cliente de prueba','usuario','id_usuario','correo','^demo10[.]portal[.]([0-9]{2})@example[.]invalid$'),
    ('Canales de difusión','canal_difusion','id_canal','nombre_canal','^DEMO10 · '),
    ('Campañas','campana','id_campana','nombre','^DEMO10 · Campaña ([0-9]{2}) · '),
    ('Audiencias','segmento_audiencia','id_segmento','nombre','^DEMO10 · Audiencia ([0-9]{2})$'),
    ('Cotizaciones','crm_acuerdo','id','nombre','^DEMO10 · Cotización ([0-9]{2})$'),
    ('Contratos','crm_acuerdo','id','nombre','^DEMO10 · Contrato ([0-9]{2})$'),
    ('Cuentas publicitarias simuladas','crm_cuenta_publicitaria','id','codigo','^DEMO10-CTA-([0-9]{2})$'),
    ('Contactos / Leads','crm_contacto','id','correo','^demo10[.]contacto[.]([0-9]{2})@example[.]invalid$'),
    ('Pipeline','crm_oportunidad','id','nombre','^DEMO10 · Oportunidad ([0-9]{2})$'),
    ('Actividades','crm_actividad','id','nombre','^DEMO10 · Seguimiento ([0-9]{2})$'),
    ('Segmentos dinámicos','crm_segmento','id','nombre','^DEMO10 · Segmento ([0-9]{2})$'),
    ('Reglas de puntuación','crm_regla_score','id','nombre','^DEMO10 · Regla ([0-9]{2})$'),
    ('Metas y alertas','crm_meta','id','nombre','^DEMO10 · Meta ([0-9]{2})$'),
    ('Contenido y aprobaciones','crm_contenido','id','nombre','^DEMO10 · Contenido ([0-9]{2})$'),
    ('Anuncios / calendario editorial','crm_anuncio','id','codigo','^DEMO10-AD-([0-9]{2})$'),
    ('ENTREGABLE','crm_trabajo_servicio','id','nombre','^DEMO10 · ENTREGABLE ([0-9]{2})$'),
    ('RECOMENDACION','crm_trabajo_servicio','id','nombre','^DEMO10 · RECOMENDACION ([0-9]{2})$'),
    ('AJUSTE','crm_trabajo_servicio','id','nombre','^DEMO10 · AJUSTE ([0-9]{2})$'),
    ('INFORME','crm_trabajo_servicio','id','nombre','^DEMO10 · INFORME ([0-9]{2})$'),
    ('Automatizaciones inactivas','crm_automatizacion','id','nombre','^DEMO10 · Flujo ([0-9]{2})$'),
    ('Campos personalizados','crm_campo','id','codigo','^demo10_campo_([0-9]{2})$'),
    ('Notas','crm_nota','id','texto','^DEMO10 · Nota ([0-9]{2}):'),
    ('Eventos del CRM','crm_evento','id','detalle','^DEMO10 · Alta de demostración ([0-9]{2})$'),
    ('Auditoría de la carga','crm_auditoria','id','accion','^DEMO10_CARGA_([0-9]{2})$'),
    ('Rendimiento publicitario DEMO','crm_publicidad_demo','id','codigo','^DEMO10-SIM-([0-9]{2})$')
   ) s(modulo,tabla,pk,campo,patron) LOOP
    IF spec.modulo='Canales de difusión' THEN CONTINUE; END IF;
    EXECUTE format('INSERT INTO demo_ids SELECT $1,substring(%I from $2)::int,%I FROM public.%I WHERE %I ~ $2',spec.campo,spec.pk,spec.tabla,spec.campo) USING spec.modulo,spec.patron;
    GET DIAGNOSTICS cantidad=ROW_COUNT;
    IF cantidad<>10 THEN RAISE EXCEPTION 'Se esperaban 10 registros originales en %, encontrados %. No se modificó la carga.',spec.modulo,cantidad; END IF;
   END LOOP;
   INSERT INTO demo_ids SELECT 'Empresas',substring(c.correo from '^demo10[.]empresa[.]([0-9]{2})@')::int,e.id FROM public.cliente c JOIN public.crm_cuenta e ON e.cliente_id=c.id_cliente AND e.fusionada_en IS NULL WHERE c.correo ~ '^demo10[.]empresa[.][0-9]{2}@example[.]invalid$';
   INSERT INTO demo_ids SELECT 'Canales de difusión',d.lote,c.id_canal FROM demo_ids d JOIN public.campana c ON c.id_campana=d.id JOIN public.canal_difusion cd ON cd.id_canal=c.id_canal WHERE d.modulo='Campañas' AND cd.nombre_canal LIKE 'DEMO10 · %';
   INSERT INTO demo_ids SELECT 'Accesos al portal desactivados',d.lote,p.usuario_id FROM demo_ids d JOIN public.crm_portal_acceso p ON p.usuario_id=d.id WHERE d.modulo='Perfiles de cliente de prueba' AND NOT p.activo;
  END IF;
 END IF;
 IF (SELECT count(DISTINCT modulo) FROM demo_ids)<>28 OR EXISTS(SELECT 1 FROM demo_ids GROUP BY modulo HAVING count(*)<>10) THEN
  RAISE EXCEPTION 'Inventario incompleto del lote; se cancela el cambio de presentación';
 END IF;
 INSERT INTO public.crm_empresa_migracion(tipo,registro_id,datos)
  SELECT 'lote_marketing_20261124',0,jsonb_build_object('simulado',true,'fuente_estadisticas','Mockaroo','origen_registros','Carga inicial','version',1,'registros',jsonb_agg(to_jsonb(d) ORDER BY modulo,lote)) FROM demo_ids d
  ON CONFLICT(tipo,registro_id) DO NOTHING;
END $$;

CREATE TEMP TABLE nombres_marketing(lote int PRIMARY KEY,empresa text,dominio text,contacto text,correo text,trabajador text,correo_equipo text,campana text,interes text,canal text,fuente text,campo text,codigo_campo text,valor_campo text) ON COMMIT DROP;
INSERT INTO nombres_marketing VALUES
 (1,'Andina Studio','andinastudio','Camila Torres','camila.torres','Sofía Herrera','sofia.herrera','Identidad que conecta','Diseño y creatividad','Facebook · Alcance','facebook','Objetivo de marketing','objetivo_marketing','Reconocimiento de marca'),
 (2,'Nova Retail','novaretail','Diego Mendoza','diego.mendoza','Luis Ramírez','luis.ramirez','Nueva temporada','Compras y tendencias','Instagram · Comunidad','instagram','Producto de interés','producto_interes','Colección de temporada'),
 (3,'Horizonte Digital','horizontedigital','Valentina Rojas','valentina.rojas','Ana Beltrán','ana.beltran','Impulso digital','Soluciones digitales','TikTok · Descubrimiento','tiktok','Canal preferido','canal_preferido','Correo electrónico'),
 (4,'Café Aurora','cafeaurora','Mateo Campos','mateo.campos','Carlos Ríos','carlos.rios','El sabor de cada mañana','Café de especialidad','YouTube · Video','youtube','Horario de contacto','horario_contacto','Mañana'),
 (5,'Eco Hogar','ecohogar','Lucía Pérez','lucia.perez','Elena Vargas','elena.vargas','Un hogar más sostenible','Consumo responsable','Búsqueda · Captación','google','Categoría de interés','categoria_interes','Hogar sostenible'),
 (6,'Nexo Tecnología','nexotecnologia','Sebastián Vega','sebastian.vega','Javier Molina','javier.molina','Tecnología para avanzar','Innovación y tecnología','Email · Fidelización','newsletter','Prioridad comercial','prioridad_comercial','Seguimiento mensual'),
 (7,'Brisa Viajes','brisaviajes','Daniela Castro','daniela.castro','Natalia Sánchez','natalia.sanchez','Tu próxima escapada','Turismo y experiencias','Display · Remarketing','display','Destino de interés','destino_interes','Turismo nacional'),
 (8,'Origen Natural','origennatural','Gabriel Flores','gabriel.flores','Ricardo Peña','ricardo.pena','Bienestar desde el origen','Bienestar y nutrición','LinkedIn · Empresas','linkedin','Línea de servicio','linea_servicio','Bienestar'),
 (9,'Impulso Educación','impulsoeducacion','Mariana Ruiz','mariana.ruiz','Andrea Salazar','andrea.salazar','Aprende y transforma','Formación profesional','Web · Contenido editorial','blog','Programa de interés','programa_interes','Marketing digital'),
 (10,'Luna Moda','lunamoda','Nicolás Medina','nicolas.medina','Fernando Acosta','fernando.acosta','Tu estilo, tu momento','Moda y estilo','Podcast · Reconocimiento','podcast','Preferencia de contenido','preferencia_contenido','Novedades y colecciones');

-- Solo se cambian los valores originales; una edición posterior se conserva.
UPDATE public.cliente c SET nombre=n.empresa,correo='contacto@'||n.dominio||'.example.invalid',updated_at=now()
 FROM demo_ids d JOIN nombres_marketing n USING(lote) JOIN public.crm_cuenta e ON e.id=d.id
 WHERE d.modulo='Empresas' AND c.id_cliente=e.cliente_id AND c.correo LIKE 'demo10.empresa.%@example.invalid';
UPDATE public.crm_cuenta e SET sitio_web='https://'||n.dominio||'.example.invalid'
 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Empresas' AND e.id=d.id AND e.sitio_web LIKE 'https://empresa-%.example.invalid';
UPDATE public.usuario u SET nombres=CASE WHEN d.modulo='Trabajadores de prueba' THEN n.trabajador ELSE n.contacto END,
 correo=CASE WHEN d.modulo='Trabajadores de prueba' THEN n.correo_equipo||'@agencia.example.invalid' ELSE n.correo||'@clientes.example.invalid' END,updated_at=now()
 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo IN('Trabajadores de prueba','Perfiles de cliente de prueba') AND u.id_usuario=d.id AND u.correo LIKE 'demo10.%@example.invalid';
UPDATE public.canal_difusion c SET nombre_canal=n.canal FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Canales de difusión' AND c.id_canal=d.id AND c.nombre_canal LIKE 'DEMO10 · %';
UPDATE public.campana c SET nombre=n.campana||' · '||n.empresa,objetivo='Aumentar el reconocimiento de marca, atraer tráfico cualificado y medir las acciones publicitarias.',updated_at=now()
 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Campañas' AND c.id_campana=d.id AND c.nombre LIKE 'DEMO10 · %';
UPDATE public.segmento_audiencia a SET nombre=n.interes||' · '||n.empresa,descripcion='Audiencia interesada en '||lower(n.interes)||'.',criterios='Personas adultas con afinidad temática por '||lower(n.interes)||'.',updated_at=now()
 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Audiencias' AND a.id_segmento=d.id AND a.nombre LIKE 'DEMO10 · %';
UPDATE public.interes t SET nombre_interes=n.interes FROM demo_ids d JOIN nombres_marketing n USING(lote) JOIN public.segmento_interes si ON si.id_segmento=d.id
 WHERE d.modulo='Audiencias' AND t.id_interes=si.id_interes AND t.nombre_interes LIKE 'DEMO10 · %';
UPDATE public.crm_acuerdo a SET nombre=CASE WHEN a.tipo='CONTRATO' THEN 'Plan de marketing · ' ELSE 'Propuesta integral · ' END||n.empresa,
 alcance='Planificación de contenido, producción, revisión de materiales, seguimiento de campañas e informes de rendimiento.',
 condiciones='Entregas semanales, revisión de materiales y seguimiento mensual del alcance acordado.',version=version+1
 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo IN('Contratos','Cotizaciones') AND a.id=d.id AND a.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_cuenta_publicitaria a SET nombre=n.empresa||' · '||a.plataforma,codigo='CTA-20261124-'||lpad(n.lote::text,2,'0')
 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Cuentas publicitarias simuladas' AND a.id=d.id AND a.codigo LIKE 'DEMO10-CTA-%';
UPDATE public.crm_etiqueta t SET nombre=n.empresa||' · Interés comercial' FROM nombres_marketing n WHERE t.nombre='DEMO10-'||lpad(n.lote::text,2,'0');
UPDATE public.crm_contacto c SET nombre=n.contacto,correo=n.correo||'@contactos.example.invalid',origen=n.fuente,etiquetas=n.empresa||' · Interés comercial',updated_at=now()
 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Contactos / Leads' AND c.id=d.id AND c.correo LIKE 'demo10.contacto.%@example.invalid';
UPDATE public.crm_oportunidad o SET nombre='Propuesta comercial · '||n.empresa,version=version+1,updated_at=now() FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Pipeline' AND o.id=d.id AND o.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_actividad a SET nombre='Revisión de propuesta · '||n.empresa,descripcion='Revisar los objetivos, el alcance y los próximos pasos de la propuesta.',version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Actividades' AND a.id=d.id AND a.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_segmento s SET nombre='Interés comercial · '||n.empresa,etiqueta=n.empresa||' · Interés comercial',origen=n.fuente,version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Segmentos dinámicos' AND s.id=d.id AND s.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_regla_score r SET nombre='Afinidad con '||n.empresa,valor=n.empresa||' · Interés comercial',version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Reglas de puntuación' AND r.id=d.id AND r.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_meta m SET nombre='Captación de contactos · '||n.empresa,version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Metas y alertas' AND m.id=d.id AND m.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_contenido c SET nombre='Presentación de marca · '||n.empresa,texto=n.campana||'. Descubre las novedades de '||n.empresa||' y conoce nuestra propuesta.',version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Contenido y aprobaciones' AND c.id=d.id AND c.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_anuncio a SET nombre=n.campana||' · Pieza principal',codigo='AD-20261124-'||lpad(n.lote::text,2,'0'),version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Anuncios / calendario editorial' AND a.id=d.id AND a.codigo LIKE 'DEMO10-AD-%';
UPDATE public.crm_trabajo_servicio t SET nombre=CASE t.tipo WHEN 'ENTREGABLE' THEN 'Propuesta creativa' WHEN 'RECOMENDACION' THEN 'Optimización de audiencias' WHEN 'AJUSTE' THEN 'Revisión de segmentación' ELSE 'Informe de rendimiento' END||' · '||n.empresa,
 descripcion=CASE t.tipo WHEN 'ENTREGABLE' THEN 'Preparar el concepto creativo, el texto y la ficha del contenido.' WHEN 'RECOMENDACION' THEN 'Revisar el CTR por país y priorizar las audiencias con mejor interacción.' WHEN 'AJUSTE' THEN 'Evaluar la distribución de audiencias antes de solicitar cambios de segmentación.' ELSE 'Consolidar impresiones, clics, conversiones e inversión por campaña y país.' END,version=version+1
 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo IN('ENTREGABLE','RECOMENDACION','AJUSTE','INFORME') AND t.id=d.id AND t.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_automatizacion a SET nombre='Seguimiento comercial · '||n.empresa,asunto='Revisar propuesta de '||n.empresa,mensaje='Crear un recordatorio interno para revisar la propuesta y registrar los próximos pasos.',version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Automatizaciones inactivas' AND a.id=d.id AND a.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_campo c SET nombre=n.campo,codigo=n.codigo_campo,version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Campos personalizados' AND c.id=d.id AND c.codigo LIKE 'demo10_campo_%';
UPDATE public.crm_campo_valor v SET valor=to_jsonb(n.valor_campo) FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Campos personalizados' AND v.campo_id=d.id AND v.valor#>>'{}' LIKE 'Valor de prueba %';
UPDATE public.crm_origen o SET canal=n.canal,utm_source=n.fuente,utm_medium=CASE WHEN n.fuente='newsletter' THEN 'email' WHEN n.fuente IN('blog','podcast') THEN 'contenido' ELSE 'publicidad' END,utm_campaign=n.dominio||'_lanzamiento' FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Contactos / Leads' AND o.contacto_id=d.id AND o.utm_source='demo10';
UPDATE public.crm_formulario f SET nombre='Consulta comercial · '||n.empresa FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Campañas' AND f.campana_id=d.id AND f.nombre LIKE 'DEMO10 · %';
UPDATE public.crm_nota a SET texto='Objetivos de '||n.empresa||': fortalecer la marca, mejorar la captación y dar seguimiento a las consultas.' FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Notas' AND a.id=d.id AND a.texto LIKE 'DEMO10 · %';
UPDATE public.crm_evento e SET tipo='INCORPORACION',detalle='Incorporación de la ficha comercial de '||n.empresa FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Eventos del CRM' AND e.id=d.id AND e.detalle LIKE 'DEMO10 · %';
UPDATE public.crm_auditoria a SET accion='IMPORTACION_LOTE_MARKETING_'||lpad(n.lote::text,2,'0') FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Auditoría de la carga' AND a.id=d.id AND a.accion LIKE 'DEMO10_CARGA_%';
UPDATE public.crm_inversion v SET concepto='Producción creativa · '||n.empresa,version=version+1 FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Campañas' AND v.campana_id=d.id AND v.concepto LIKE 'DEMO10 · %';
UPDATE public.crm_publicidad_demo a SET codigo='MKT-20261124-'||lpad(n.lote::text,2,'0') FROM demo_ids d JOIN nombres_marketing n USING(lote) WHERE d.modulo='Rendimiento publicitario DEMO' AND a.id=d.id AND a.codigo LIKE 'DEMO10-SIM-%';

DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM demo_ids d JOIN public.usuario u ON u.id_usuario=d.id WHERE d.modulo IN('Trabajadores de prueba','Perfiles de cliente de prueba') AND (u.estado<>'INACTIVO' OR u.id_supabase IS NOT NULL OR u.password_hash IS NOT NULL)) THEN RAISE EXCEPTION 'La presentación no puede modificar los accesos de autenticación'; END IF;
END $$;
INSERT INTO normalizacion_resumen SELECT modulo,count(*) FROM demo_ids GROUP BY modulo;
