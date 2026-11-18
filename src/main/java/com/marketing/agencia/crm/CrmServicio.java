package com.marketing.agencia.crm;

import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.marketing.agencia.crm.CrmControlador.*;

@Service
@RequiredArgsConstructor
public class CrmServicio {
    private final JdbcTemplate jdbc;
    private final CrmAcceso acceso;
    private final CrmEvolucionServicio evolucion;
    private static final Set<String> ESTADOS = Set.of("NUEVO","CONTACTADO","CALIFICADO","CLIENTE","DESCARTADO");
    private String tabla(String recurso) { return recurso.equals("contactos") ? "public.crm_contacto" : "public.crm_cuenta"; }
    private String columna(String recurso) { return recurso.equals("contactos") ? "contacto_id" : "cuenta_id"; }
    public Map<String,Object> capacidades(UUID sujeto) {
        Map<String,Object> resultado = new LinkedHashMap<>();
        for (String recurso : List.of("contactos","cuentas")) for (boolean escribir : List.of(false,true)) {
            boolean permitido;
            try { acceso.exigir(sujeto,recurso,escribir); permitido=true; }
            catch (ExcepcionApi e) { permitido=false; }
            resultado.put(recurso+(escribir ? "Gestionar" : "Leer"),permitido);
        }
        return resultado;
    }
    public Map<String,Object> opciones(String recurso,UUID sujeto) {
        acceso.exigir(sujeto,recurso,true);
        return Map.of("cuentas",filas("select a.id,a.nombre || case when cl.estado='INACTIVO' then ' (archivada)' else '' end nombre from public.crm_cuenta a join public.cliente cl on cl.id_cliente=a.cliente_id where a.fusionada_en is null order by a.nombre limit 1000"),
            "campanas",filas("select id_campana id,nombre from public.campana order by nombre limit 1000"),
            "responsables",filas("select id_usuario id,nombres nombre from public.usuario where upper(estado)='ACTIVO' order by nombres limit 1000"),
            "clientes",filas("select id_cliente id,nombre from public.cliente where upper(estado)='ACTIVO' order by nombre limit 1000"));
    }
    private String filtro(String recurso) { String a=recurso.equals("contactos")?"c":"cl";return " where (lower(c.nombre) like ? escape '!' or lower(coalesce("+a+".correo,'')) like ? escape '!' or coalesce("+a+".telefono,'') like ? escape '!') and (?='' or "+a+".estado=?)"+(recurso.equals("contactos")?"":" and c.fusionada_en is null"); }
    private List<Object> parametros(String recurso,String q,String estado) {
        if (q.length()>160 || (!estado.isEmpty() && !(recurso.equals("contactos")?ESTADOS:Set.of("ACTIVO","INACTIVO")).contains(estado))) throw invalido("Revisa la búsqueda o el estado");
        String patron="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        List<Object> params=new ArrayList<>(List.of(patron));
        params.addAll(List.of(patron,patron,estado,estado));
        return params;
    }
    private String seleccion(String recurso) {
        return recurso.equals("contactos") ? "select c.*,a.nombre cuenta,u.nombres responsable,ca.nombre campana from public.crm_contacto c left join public.crm_cuenta a on a.id=c.cuenta_id join public.usuario u on u.id_usuario=c.responsable_id left join public.campana ca on ca.id_campana=c.campana_id" : "select c.*,cl.correo,cl.telefono,cl.estado,greatest(c.updated_at,cl.updated_at) updated_at from public.crm_cuenta c join public.cliente cl on cl.id_cliente=c.cliente_id";
    }
    public Map<String,Object> listar(String recurso,String q,String estado,int pagina,UUID sujeto) {
        acceso.exigir(sujeto,recurso,false);
        if (pagina<0 || pagina>100000) throw invalido("Página inválida");
        var params=parametros(recurso,q,estado);
        long total=jdbc.queryForObject("select count(*) from "+tabla(recurso)+" c"+(recurso.equals("contactos")?"":" join public.cliente cl on cl.id_cliente=c.cliente_id")+filtro(recurso),Long.class,params.toArray());
        params.add(20); params.add((long)pagina*20);
        return Map.of("filas",filas(seleccion(recurso)+filtro(recurso)+" order by c.id desc limit ? offset ?",params.toArray()),"total",total,"pagina",pagina,"tamano",20);
    }
    private Map<String,Object> existente(String recurso,long id) {
        var resultado=filas(seleccion(recurso)+" where c.id="+(recurso.equals("contactos")?"?":"(select coalesce(fusionada_en,id) from public.crm_cuenta where id=?)"),id);
        if (resultado.isEmpty()) throw noEncontrado();
        return resultado.getFirst();
    }
    public Map<String,Object> detalle(String recurso,long id,UUID sujeto) {
        acceso.exigir(sujeto,recurso,false);
        var registro=existente(recurso,id);
        id=((Number)registro.get("id")).longValue();
        String columna=columna(recurso);
        registro.put("notas",filas("select n.id,n.texto,n.created_at,u.nombres autor from public.crm_nota n join public.usuario u on u.id_usuario=n.autor_id where n."+columna+"=? order by n.id desc",id));
        registro.put("eventos",filas("select e.id,e.tipo,e.detalle,e.created_at,u.nombres autor from public.crm_evento e join public.usuario u on u.id_usuario=e.autor_id where e."+columna+"=? order by e.id desc",id));
        registro.put("archivos",filas("select id,nombre,octet_length(contenido) tamano,created_at from public.crm_archivo where "+columna+"=? order by id desc",id));
        if (recurso.equals("contactos")) registro.put("consentimientos",filas("select id,tipo,canal,fecha,revocado_en from public.crm_consentimiento where contacto_id=? order by id desc",id));
        else {
            registro.put("contactos",filas("select id,nombre,correo,estado from public.crm_contacto where cuenta_id=? order by nombre",id));
            registro.put("campanas",filas("select id_campana id,nombre,estado from public.campana where id_cliente=? order by id_campana desc",registro.get("clienteId")));
            registro.put("oportunidades",filas("select o.id,o.nombre,o.valor,o.moneda,e.nombre etapa,e.tipo estado from public.crm_oportunidad o join public.crm_etapa e on e.id=o.etapa_id join public.crm_contacto c on c.id=o.contacto_id where c.cuenta_id=? order by o.id desc",id));
            registro.put("actividades",filas("select a.id,a.nombre,a.estado,a.fecha from public.crm_actividad a left join public.crm_contacto c on c.id=a.contacto_id left join public.crm_oportunidad o on o.id=a.oportunidad_id left join public.crm_contacto co on co.id=o.contacto_id where a.cuenta_id=? or c.cuenta_id=? or co.cuenta_id=? order by a.fecha desc limit 100",id,id,id));
        }
        return registro;
    }
    @Transactional
    public Map<String,Object> guardar(String recurso,Long id,Registro d,UUID sujeto) {
        long autor=acceso.exigir(sujeto,recurso,true);
        Map<String,Object> anterior=id==null ? Map.of() : existente(recurso,id);
        if(id!=null)id=((Number)anterior.get("id")).longValue();
        try {
            Long resultado=guardarDatos(recurso,id,d,autor,false);
            if(id==null&&recurso.equals("contactos"))evolucion.origenInicial(resultado,d.campanaId(),"MANUAL");
            evento(recurso,resultado,id==null ? "CREACION" : "ACTUALIZACION",id==null ? "Registro creado" : "Ficha actualizada",autor);
            if (id!=null && recurso.equals("contactos")) {
                if (!Objects.equals(anterior.get("estado"),d.estado())) evento(recurso,id,"ESTADO","Estado: "+anterior.get("estado")+" → "+d.estado(),autor);
                if (!Objects.equals(anterior.get("responsableId"),d.responsableId())) evento(recurso,id,"ASIGNACION","Responsable actualizado",autor);
            }
            return Map.of("id",resultado);
        } catch (org.springframework.dao.DuplicateKeyException e) { throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_DUPLICADO",recurso.equals("contactos")?"Ya existe un contacto con ese correo o teléfono":"La empresa ya tiene una ficha. Ábrela desde Clientes / Empresas"); }
    }
    private Long guardarDatos(String recurso,Long id,Registro d,long autor,boolean importar) {
        if (recurso.equals("cuentas")) {
            if(d.nombre().trim().length()>150)throw invalido("El nombre de empresa admite hasta 150 caracteres");
            String email=limpiar(d.correo()),telefono=normalizarTelefono(d.telefono());
            if(id!=null){var previo=existente(recurso,id);if(d.correo()==null)email=(String)previo.get("correo");if(d.telefono()==null)telefono=(String)previo.get("telefono");}
            if(email!=null){email=email.toLowerCase(Locale.ROOT);if(email.length()>150)throw invalido("El correo admite hasta 150 caracteres");}
            if(email!=null){
                jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",email);
                long excluir=id==null?0:((Number)existente(recurso,id).get("clienteId")).longValue();
                if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from public.cliente where lower(correo)=? and id_cliente<>?)",Boolean.class,email,excluir)))throw new ExcepcionApi(HttpStatus.CONFLICT,"CRM_EMPRESA_DUPLICADA","Ya existe una empresa con ese correo. Busca su ficha antes de crear otra");
            }
            String estado=d.estado()==null?(id==null?"ACTIVO":existente(recurso,id).get("estado").toString()):d.estado();if(!Set.of("ACTIVO","INACTIVO").contains(estado))throw invalido("Selecciona el estado de la empresa");
            String web=limpiar(d.sitioWeb());
            if (web!=null) { try { URI uri=URI.create(web); if (!Set.of("https","http").contains(uri.getScheme()) || uri.getHost()==null) throw invalido("El sitio web debe comenzar con https:// o http://"); } catch (IllegalArgumentException e) { throw invalido("Sitio web inválido"); } }
            if(id==null){
                if(d.clienteId()!=null)throw invalido("El cliente ya dispone de ficha unificada. Edítala desde Clientes / Empresas");
                long cliente=jdbc.queryForObject("insert into public.cliente(nombre,correo,telefono,estado,created_at,updated_at) values(?,?,?,?,now(),now()) returning id_cliente",Long.class,d.nombre().trim(),email,telefono,estado);
                id=jdbc.queryForObject("select id from public.crm_cuenta where cliente_id=? and fusionada_en is null",Long.class,cliente);
            }else jdbc.update("update public.cliente set nombre=?,correo=?,telefono=?,estado=?,updated_at=now() where id_cliente=(select cliente_id from public.crm_cuenta where id=?)",d.nombre().trim(),email,telefono,estado,id);
            jdbc.update("update public.crm_cuenta set sector=?,sitio_web=?,updated_at=now() where id=?",limpiar(d.sector()),web,id);
            return id;
        }
        String correo=limpiar(d.correo()); if (correo!=null) correo=correo.toLowerCase(Locale.ROOT);
        String telefono=normalizarTelefono(d.telefono());
        if (correo==null && telefono==null) throw invalido("Indica un correo o teléfono");
        if (d.estado()==null || !ESTADOS.contains(d.estado())) throw invalido("Selecciona un estado válido");
        if (limpiar(d.origen())==null) throw invalido("Indica el origen del contacto");
        long responsable=d.responsableId()==null ? autor : d.responsableId();
        if (jdbc.queryForObject("select count(*) from public.usuario where id_usuario=? and upper(estado)='ACTIVO'",Integer.class,responsable)==0) throw invalido("El responsable debe ser un usuario activo");
        referencia("public.crm_cuenta","id",d.cuentaId()); referencia("public.campana","id_campana",d.campanaId());
        Long cuenta=d.cuentaId();
        if(cuenta!=null)cuenta=jdbc.queryForObject("select coalesce(fusionada_en,id) from public.crm_cuenta where id=?",Long.class,cuenta);
        if(d.campanaId()!=null){
            Long empresaCampana=jdbc.queryForObject("select a.id from public.campana ca join public.crm_cuenta a on a.cliente_id=ca.id_cliente and a.fusionada_en is null where ca.id_campana=?",Long.class,d.campanaId());
            if(cuenta==null)cuenta=empresaCampana;
            else if(!cuenta.equals(empresaCampana))throw invalido("La campaña de origen debe pertenecer a la empresa del contacto");
        }
        if (id==null) {
            var resultado=jdbc.queryForList("insert into public.crm_contacto(nombre,correo,telefono,estado,origen,etiquetas,cuenta_id,campana_id,responsable_id) values(?,?,?,?,?,?,?,?,?)"+(importar ? " on conflict do nothing" : "")+" returning id",d.nombre().trim(),correo,telefono,d.estado(),d.origen().trim(),Objects.toString(d.etiquetas(),""),cuenta,d.campanaId(),responsable);
            return resultado.isEmpty() ? null : ((Number)resultado.getFirst().get("id")).longValue();
        }
        jdbc.update("update public.crm_contacto set nombre=?,correo=?,telefono=?,estado=?,origen=?,etiquetas=?,cuenta_id=?,campana_id=?,responsable_id=?,updated_at=now() where id=?",d.nombre().trim(),correo,telefono,d.estado(),d.origen().trim(),Objects.toString(d.etiquetas(),""),cuenta,d.campanaId(),responsable,id);
        return id;
    }
    static String normalizarTelefono(String valor) {
        String telefono=limpiar(valor); if (telefono==null) return null;
        telefono=telefono.replaceAll("[\\s().-]","");
        if (!telefono.matches("\\+?[0-9]{7,15}")) throw invalido("El teléfono debe tener entre 7 y 15 dígitos, con prefijo internacional si corresponde");
        // El prefijo '+' no distingue dos contactos con los mismos dígitos.
        return telefono.replace("+","");
    }
    private void referencia(String tabla,String clave,Long id) { if (id!=null && jdbc.queryForObject("select count(*) from "+tabla+" where "+clave+"=?",Integer.class,id)==0) throw invalido("La cuenta, campaña o cliente seleccionado ya no existe"); }
    @Transactional
    public void eliminar(String recurso,long id,UUID sujeto) {
        long autor=acceso.exigir(sujeto,recurso,true); id=((Number)existente(recurso,id).get("id")).longValue();
        jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values(?,?,?,?)",recurso,id,recurso.equals("cuentas")?"ARCHIVAR":"ELIMINACION",autor);
        if(recurso.equals("cuentas")){jdbc.update("update public.cliente set estado='INACTIVO',updated_at=now() where id_cliente=(select cliente_id from public.crm_cuenta where id=?)",id);evento(recurso,id,"ARCHIVO","Empresa archivada; se conserva su historial",autor);}
        else jdbc.update("delete from "+tabla(recurso)+" where id=?",id);
    }
    @Transactional
    public void nota(String recurso,long id,String texto,UUID sujeto) { long autor=acceso.exigir(sujeto,recurso,true); id=((Number)existente(recurso,id).get("id")).longValue(); jdbc.update("insert into public.crm_nota("+columna(recurso)+",texto,autor_id) values(?,?,?)",id,texto.trim(),autor); evento(recurso,id,"NOTA","Nota añadida",autor); }
    @Transactional
    public void consentimiento(long id,Consentimiento d,UUID sujeto) {
        long autor=acceso.exigir(sujeto,"contactos",true); existente("contactos",id);
        if (d.fecha().isAfter(java.time.OffsetDateTime.now())) throw invalido("El consentimiento no puede tener fecha futura");
        jdbc.update("insert into public.crm_consentimiento(contacto_id,tipo,canal,fecha,registrado_por) values(?,?,?,?,?)",id,d.tipo().trim(),d.canal().trim(),d.fecha(),autor);
        evento("contactos",id,"CONSENTIMIENTO","Consentimiento registrado: "+d.tipo()+" / "+d.canal(),autor);
    }
    @Transactional
    public void revocar(long id,long consentimiento,UUID sujeto) {
        long autor=acceso.exigir(sujeto,"contactos",true); existente("contactos",id);
        if (jdbc.update("update public.crm_consentimiento set revocado_en=now() where id=? and contacto_id=? and revocado_en is null",consentimiento,id)==0) throw noEncontrado();
        evento("contactos",id,"CONSENTIMIENTO","Consentimiento revocado",autor);
    }
    @Transactional
    public Map<String,Object> importar(List<Registro> registros,UUID sujeto) {
        long autor=acceso.exigir(sujeto,"contactos",true); int creados=0; List<Integer> duplicadas=new ArrayList<>();
        for (int i=0;i<registros.size();i++) {
            try {
                Long id=guardarDatos("contactos",null,registros.get(i),autor,true);
                if (id==null) duplicadas.add(i+2);
                else { creados++; evolucion.origenInicial(id,registros.get(i).campanaId(),"CSV"); evento("contactos",id,"IMPORTACION","Contacto importado desde CSV",autor); }
            } catch (ExcepcionApi e) { throw invalido("Fila "+(i+2)+": "+e.getMessage()+". No se importó ninguna fila."); }
        }
        return Map.of("creados",creados,"omitidos",duplicadas.size(),"filasDuplicadas",duplicadas);
    }
    public byte[] exportar(String q,String estado,UUID sujeto) {
        acceso.exigir(sujeto,"contactos",false);
        var params=parametros("contactos",q,estado);
        var datos=filas("select c.nombre,c.correo,c.telefono,c.estado,c.origen,c.etiquetas,c.cuenta_id,c.campana_id,c.responsable_id from public.crm_contacto c"+filtro("contactos")+" order by c.id limit 10001",params.toArray());
        if (datos.size()>10000) throw invalido("Filtra la exportación a un máximo de 10000 contactos");
        List<String> campos=List.of("nombre","correo","telefono","estado","origen","etiquetas","cuentaId","campanaId","responsableId");
        StringBuilder csv=new StringBuilder("\uFEFF"+String.join(",",campos)+"\r\n");
        for (var dato:datos) csv.append(campos.stream().map(c->csvCelda(dato.get(c))).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    static String csvCelda(Object valor) { String texto=Objects.toString(valor,""); if (!texto.stripLeading().isEmpty() && "=+@-".indexOf(texto.stripLeading().charAt(0))>=0) texto="'"+texto; return "\""+texto.replace("\"","\"\"")+"\""; }
    @Transactional
    public void archivo(String recurso,long id,String nombre,byte[] contenido,UUID sujeto) {
        long autor=acceso.exigir(sujeto,recurso,true); id=((Number)existente(recurso,id).get("id")).longValue();
        String seguro=Objects.toString(nombre,"archivo").replaceAll("[\\p{Cntrl}/\\\\]","_");
        if (seguro.length()>200) seguro=seguro.substring(seguro.length()-200);
        jdbc.update("insert into public.crm_archivo("+columna(recurso)+",nombre,contenido,autor_id) values(?,?,?,?)",id,seguro,contenido,autor);
        evento(recurso,id,"ARCHIVO","Archivo adjuntado",autor);
    }
    public Map<String,Object> descargar(String recurso,long id,long archivo,UUID sujeto) {
        acceso.exigir(sujeto,recurso,false);
        id=((Number)existente(recurso,id).get("id")).longValue();
        var datos=jdbc.queryForList("select nombre,contenido from public.crm_archivo where id=? and "+columna(recurso)+"=?",archivo,id);
        if (datos.isEmpty()) throw noEncontrado(); return datos.getFirst();
    }
    private void evento(String recurso,long id,String tipo,String detalle,long autor) { jdbc.update("insert into public.crm_evento("+columna(recurso)+",tipo,detalle,autor_id) values(?,?,?,?)",id,tipo,detalle,autor); }
    private List<Map<String,Object>> filas(String sql,Object... params) {
        return jdbc.queryForList(sql,params).stream().map(fila->{ Map<String,Object> salida=new LinkedHashMap<>(); fila.forEach((clave,valor)->{ String[] partes=clave.split("_"); StringBuilder camel=new StringBuilder(partes[0]); for(int i=1;i<partes.length;i++) camel.append(Character.toUpperCase(partes[i].charAt(0))).append(partes[i].substring(1)); salida.put(camel.toString(),valor); }); return salida; }).toList();
    }
    private static String limpiar(String valor) { return valor==null || valor.isBlank() ? null : valor.trim(); }
    private static ExcepcionApi invalido(String texto) { return new ExcepcionApi(HttpStatus.BAD_REQUEST,"CRM_DATOS_INVALIDOS",texto); }
    private ExcepcionApi noEncontrado() { return new ExcepcionApi(HttpStatus.NOT_FOUND,"CRM_NO_ENCONTRADO","El registro solicitado ya no existe"); }
}
