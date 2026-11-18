package com.marketing.agencia.crm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.agencia.config.ExcepcionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.marketing.agencia.crm.CrmOperacionServicio.*;

@Service @RequiredArgsConstructor
public class CrmCamposServicio {
    private final JdbcTemplate jdbc;
    private final CrmAcceso acceso;
    private final ObjectMapper json;
    public Map<String,Object> catalogo(int pagina,UUID sujeto){
        acceso.exigir(sujeto,"campos",false);if(pagina<0||pagina>10000)throw invalido("Página inválida");
        return Map.of("filas",definiciones(false,25,pagina*25),"total",jdbc.queryForObject("select count(*) from public.crm_campo",Long.class));
    }
    List<Map<String,Object>> definiciones(boolean activas,int limite,int offset){
        var filas=jdbc.queryForList("select id,codigo,nombre,tipo,opciones::text,activo,version from public.crm_campo where (?=false or activo) order by id limit ? offset ?",activas,limite,offset);
        for(var f:filas)f.put("opciones",leer(f.get("opciones").toString(),new TypeReference<List<String>>(){}));return filas;
    }
    @Transactional
    public Map<String,Object> guardar(Long id,Map<String,Object>d,UUID sujeto){
        long autor=acceso.exigir(sujeto,"campos",true);
        String codigo=texto(d,"codigo",60,true),nombre=texto(d,"nombre",120,true),tipo=opcion(d,"tipo","TEXTO","NUMERO","FECHA","BOOLEANO","OPCION");
        if(!codigo.matches("[a-z][a-z0-9_]{0,59}"))throw invalido("La clave debe comenzar con letra minúscula y contener solo letras, números o guion bajo");
        var opciones=opciones(d.get("opciones"));if(tipo.equals("OPCION")&&opciones.isEmpty())throw invalido("Añade al menos una opción");if(!tipo.equals("OPCION"))opciones=List.of();
        if(id==null){
            jdbc.queryForList("select pg_advisory_xact_lock(hashtext('crm_catalogo_campos'))");
            if(jdbc.queryForObject("select count(*) from public.crm_campo",Long.class)>=100)throw invalido("El catálogo admite hasta 100 campos");
            id=jdbc.queryForObject("insert into public.crm_campo(codigo,nombre,tipo,opciones,activo) values(?,?,?,?::jsonb,?) returning id",Long.class,codigo,nombre,tipo,escribir(opciones),!Boolean.FALSE.equals(d.get("activo")));
        }else{
            var anteriores=jdbc.queryForList("select * from public.crm_campo where id=? for update",id);if(anteriores.isEmpty())throw invalido("Campo inexistente");var anterior=anteriores.getFirst();
            if(!codigo.equals(anterior.get("codigo"))||!tipo.equals(anterior.get("tipo")))throw invalido("La clave y el tipo son inmutables; archiva el campo y crea otro");
            if(tipo.equals("OPCION"))for(var f:jdbc.queryForList("select distinct valor #>> '{}' valor from public.crm_campo_valor where campo_id=?",id))if(!opciones.contains(f.get("valor")))throw invalido("No se puede retirar una opción que tiene valores guardados");
            if(jdbc.update("update public.crm_campo set nombre=?,opciones=?::jsonb,activo=?,version=version+1 where id=? and version=?",nombre,escribir(opciones),!Boolean.FALSE.equals(d.get("activo")),id,numero(d,"version"))==0)throw conflicto();
        }
        jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values('campos',?,'GUARDAR',?)",id,autor);return Map.of("id",id);
    }
    public Map<String,Object> valores(long contacto,UUID sujeto){
        acceso.exigir(sujeto,"contactos",false);contacto(contacto);
        Map<String,Object> valores=new LinkedHashMap<>();for(var f:jdbc.queryForList("select c.codigo,v.valor::text from public.crm_campo_valor v join public.crm_campo c on c.id=v.campo_id where v.contacto_id=? and c.activo",contacto))valores.put(f.get("codigo").toString(),leer(f.get("valor").toString(),new TypeReference<Object>(){}));
        Integer version=jdbc.queryForObject("select coalesce((select version from public.crm_campos_revision where contacto_id=?),0)",Integer.class,contacto);
        return Map.of("definiciones",definiciones(true,100,0),"valores",valores,"version",version);
    }
    @Transactional
    public void guardarValores(long contacto,long version,Map<String,Object> valores,UUID sujeto){
        long autor=acceso.exigir(sujeto,"contactos",true);contacto(contacto);
        if(valores==null||valores.size()>100||version<0)throw invalido("Campos o versión inválidos");
        jdbc.update("insert into public.crm_campos_revision(contacto_id) values(?) on conflict do nothing",contacto);
        long actual=jdbc.queryForObject("select version from public.crm_campos_revision where contacto_id=? for update",Long.class,contacto);if(actual!=version)throw conflicto();
        var defs=definiciones(true,100,0);Set<String> permitidos=new HashSet<>();for(var c:defs)permitidos.add(c.get("codigo").toString());
        if(!permitidos.containsAll(valores.keySet()))throw invalido("Hay campos archivados o desconocidos; recarga la ficha");
        // PATCH: los campos omitidos se conservan; null borra únicamente ese valor explícito.
        for(var c:defs){String clave=c.get("codigo").toString();if(!valores.containsKey(clave))continue;Object valor=validar(c,valores.get(clave));
            if(valor==null)jdbc.update("delete from public.crm_campo_valor where contacto_id=? and campo_id=?",contacto,c.get("id"));
            else jdbc.update("insert into public.crm_campo_valor(contacto_id,campo_id,valor) values(?,?,?::jsonb) on conflict(contacto_id,campo_id) do update set valor=excluded.valor",contacto,c.get("id"),escribir(valor));
        }
        jdbc.update("update public.crm_campos_revision set version=version+1 where contacto_id=?",contacto);
        jdbc.update("insert into public.crm_auditoria(recurso,registro_id,accion,autor_id) values('contactos',?,'CAMPOS',?)",contacto,autor);
    }
    static Object validar(Map<String,Object>campo,Object valor){
        if(valor==null||valor instanceof String s&&s.isBlank())return null;
        String nombre=campo.get("nombre").toString();
        return switch(campo.get("tipo").toString()){
            case "TEXTO"->{if(!(valor instanceof String s)||s.length()>2000)throw invalido(nombre+": admite hasta 2000 caracteres");yield valor;}
            case "NUMERO"->{try{if(!(valor instanceof String||valor instanceof Number))throw new IllegalArgumentException();BigDecimal n=new BigDecimal(valor.toString());if(n.precision()>24||n.scale()>6||n.abs().compareTo(new BigDecimal("999999999999999999"))>0)throw new IllegalArgumentException();yield n;}catch(Exception e){throw invalido(nombre+": número inválido (máximo 6 decimales)");}}
            case "FECHA"->{try{yield LocalDate.parse(valor.toString()).toString();}catch(Exception e){throw invalido(nombre+": fecha inválida");}}
            case "BOOLEANO"->{if(!(valor instanceof Boolean))throw invalido(nombre+": elige Sí o No");yield valor;}
            case "OPCION"->{if(!(valor instanceof String)||!((List<?>)campo.get("opciones")).contains(valor))throw invalido(nombre+": opción inválida");yield valor;}
            default->throw invalido("Tipo de campo no admitido");
        };
    }
    private List<String> opciones(Object valor){if(valor==null)return List.of();if(!(valor instanceof List<?> lista)||lista.size()>30)throw invalido("Admite hasta 30 opciones");List<String> r=new ArrayList<>();for(Object o:lista){if(!(o instanceof String s)||s.isBlank()||s.trim().length()>120)throw invalido("Revisa las opciones");String limpio=o.toString().trim();if(r.contains(limpio))throw invalido("Hay opciones repetidas");r.add(limpio);}return r;}
    private void contacto(long id){if(jdbc.queryForObject("select count(*) from public.crm_contacto where id=?",Long.class,id)==0)throw invalido("Contacto inexistente");}
    String escribir(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw invalido("Datos inválidos");}}
    <T>T leer(String s,TypeReference<T>tipo){try{return json.readValue(s,tipo);}catch(Exception e){throw new IllegalStateException("Datos CRM almacenados inválidos");}}
    private ExcepcionApi conflicto(){return new ExcepcionApi(HttpStatus.CONFLICT,"CRM_CONCURRENCIA","Otra persona modificó los campos. Recarga antes de guardar.");}
}
