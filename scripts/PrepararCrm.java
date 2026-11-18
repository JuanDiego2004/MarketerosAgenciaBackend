import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** java -cp <postgresql.jar> scripts/PrepararCrm.java [--aplicar]. Nunca imprime credenciales. */
class PrepararCrm {
    public static void main(String[] args) throws Exception {
        Map<String,String> config=new HashMap<>(System.getenv());
        Path archivo=Path.of(".env");
        if(Files.exists(archivo))for(String linea:Files.readAllLines(archivo)) {
            if(linea.isBlank()||linea.stripLeading().startsWith("#")||!linea.contains("="))continue;
            int separador=linea.indexOf('=');String clave=linea.substring(0,separador).trim(),valor=linea.substring(separador+1).trim();
            if(valor.length()>1&&(valor.startsWith("\"")&&valor.endsWith("\"")||valor.startsWith("'")&&valor.endsWith("'")))valor=valor.substring(1,valor.length()-1);
            config.putIfAbsent(clave,valor);
        }
        Properties propiedades=new Properties();propiedades.setProperty("user",config.getOrDefault("DB_USERNAME",""));propiedades.setProperty("password",config.getOrDefault("DB_PASSWORD",""));
        propiedades.setProperty("connectTimeout","10");propiedades.setProperty("socketTimeout","30");propiedades.setProperty("prepareThreshold","0");
        String url=config.get("DB_URL");if(url==null||!url.startsWith("jdbc:postgresql:"))throw new IllegalStateException("Falta DB_URL PostgreSQL");
        try(Connection c=DriverManager.getConnection(url,propiedades);Statement s=c.createStatement()) {
            s.setQueryTimeout(30);
            try(ResultSet r=s.executeQuery("select table_name,column_name,data_type from information_schema.columns where table_schema='public' and table_name in ('usuario','rol','permiso','rol_permiso','cliente','campana') order by table_name,ordinal_position")) {
                while(r.next())System.out.println(r.getString(1)+"."+r.getString(2)+": "+r.getString(3));
            }
            Set<String> existentes=new HashSet<>();try(ResultSet r=s.executeQuery("select table_name from information_schema.tables where table_schema='public' and table_name like 'crm_%'")){while(r.next())existentes.add(r.getString(1));}
            System.out.println("Tablas CRM existentes: "+new TreeSet<>(existentes));
            if(!List.of(args).contains("--aplicar")){System.out.println("Inventario de solo lectura terminado.");return;}
            List<List<String>> grupos=List.of(List.of("crm_cuenta","crm_contacto","crm_consentimiento","crm_nota","crm_evento","crm_archivo","crm_auditoria"),List.of("crm_etapa","crm_oportunidad","crm_actividad","crm_segmento","crm_regla_score","crm_origen","crm_formulario","crm_meta","crm_contenido","crm_automatizacion","crm_ejecucion","crm_gasto_meta","crm_integracion_meta"),List.of("crm_etiqueta","crm_contacto_etiqueta","crm_transicion","crm_baja_email","crm_baja_token"),List.of("crm_campo","crm_campo_valor","crm_campos_revision","crm_flujo","crm_flujo_paso"),List.of("crm_conector","crm_gasto_google","crm_analitica","crm_baja_canal","crm_envio"),List.of("crm_whatsapp_mensaje","crm_whatsapp_estado"),List.of("crm_publicidad_demo","crm_publicidad_demo_metrica"),List.of("crm_inversion"));
            for(int i=0;i<grupos.size();i++){
                var tablas=grupos.get(i);long presentes=tablas.stream().filter(existentes::contains).count();
                if(presentes==tablas.size()){System.out.println("crm-v"+(i+1)+": tablas ya presentes; no se vuelve a ejecutar.");continue;}
                if(presentes>0)throw new IllegalStateException("Esquema CRM parcial; revisar antes de aplicar la migración "+(i+1));
                s.execute(Files.readString(Path.of("src/main/resources/sql/crm-v"+(i+1)+".sql")));
                existentes.addAll(tablas);System.out.println("crm-v"+(i+1)+" aplicada correctamente.");
            }
        }catch(SQLException e){System.err.println("Operación PostgreSQL fallida. SQLSTATE="+e.getSQLState()+". No se muestran credenciales.");System.exit(1);}
    }
}
