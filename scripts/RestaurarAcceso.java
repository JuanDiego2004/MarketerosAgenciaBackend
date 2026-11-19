import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Uso manual con Java 25 y el driver PostgreSQL. La contraseña se recibe por entorno. */
class RestaurarAcceso {
    public static void main(String[] args) throws Exception {
        String correo=System.getenv().getOrDefault("CRM_BOOTSTRAP_EMAIL","admin@gmail.com");
        String password=System.getenv("CRM_BOOTSTRAP_PASSWORD");
        if(password==null||password.length()<8)throw new IllegalArgumentException("Define CRM_BOOTSTRAP_PASSWORD con al menos 8 caracteres");
        Map<String,String> env=new HashMap<>(System.getenv());
        for(String linea:Files.readAllLines(Path.of(".env"))){
            if(linea.isBlank()||linea.stripLeading().startsWith("#")||!linea.contains("="))continue;
            int separador=linea.indexOf('=');String valor=linea.substring(separador+1).trim();
            if(valor.length()>1&&((valor.startsWith("\"")&&valor.endsWith("\""))||(valor.startsWith("'")&&valor.endsWith("'"))))valor=valor.substring(1,valor.length()-1);
            env.putIfAbsent(linea.substring(0,separador).trim(),valor);
        }
        Properties propiedades=new Properties();
        propiedades.setProperty("user",env.get("DB_USERNAME"));propiedades.setProperty("password",env.get("DB_PASSWORD"));
        propiedades.setProperty("connectTimeout","10");propiedades.setProperty("socketTimeout","30");propiedades.setProperty("prepareThreshold","0");
        try(Connection c=DriverManager.getConnection(env.get("DB_URL"),propiedades)){
            c.setAutoCommit(false);
            try {
                String roles=Files.readString(Path.of("scripts/restaurar-roles-permisos.sql")).replaceFirst("(?m)^BEGIN;\\s*$","").replaceFirst("(?m)^COMMIT;\\s*$","");
                try(var s=c.createStatement()){s.setQueryTimeout(30);s.execute(roles);}
                UUID sujeto=null;
                try(var s=c.prepareStatement("select id from auth.users where lower(email)=lower(?) for update")){s.setString(1,correo);try(var r=s.executeQuery()){if(r.next())sujeto=r.getObject(1,UUID.class);if(r.next())throw new IllegalStateException("Hay varias identidades con ese correo; no se modificó el acceso");}}
                if(sujeto==null){
                    sujeto=UUID.randomUUID();
                    try(var s=c.prepareStatement("insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,confirmation_token,recovery_token,email_change_token_new,email_change,email_change_token_current,reauthentication_token,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,is_super_admin,is_sso_user,is_anonymous) values('00000000-0000-0000-0000-000000000000',?,'authenticated','authenticated',?,extensions.crypt(?,extensions.gen_salt('bf',10)),now(),'','','','','','','{\"provider\":\"email\",\"providers\":[\"email\"]}'::jsonb,'{\"email_verified\":true}'::jsonb,now(),now(),false,false,false)")){
                        s.setObject(1,sujeto);s.setString(2,correo);s.setString(3,password);s.executeUpdate();
                    }
                }else{
                    try(var s=c.prepareStatement("update auth.users set encrypted_password=extensions.crypt(?,extensions.gen_salt('bf',10)),email_confirmed_at=coalesce(email_confirmed_at,now()),updated_at=now() where id=?")){s.setString(1,password);s.setObject(2,sujeto);s.executeUpdate();}
                }
                try(var s=c.prepareStatement("insert into auth.identities(provider_id,user_id,identity_data,provider,created_at,updated_at) select ?::text,?,jsonb_build_object('sub',?::text,'email',?::text,'email_verified',true,'phone_verified',false),'email',now(),now() where not exists(select 1 from auth.identities where user_id=? and provider='email')")){
                    s.setString(1,sujeto.toString());s.setObject(2,sujeto);s.setString(3,sujeto.toString());s.setString(4,correo);s.setObject(5,sujeto);s.executeUpdate();
                }
                Long perfil=null;
                try(var s=c.prepareStatement("select id_usuario from public.usuario where lower(correo)=lower(?) or id_supabase=? for update")){s.setString(1,correo);s.setObject(2,sujeto);try(var r=s.executeQuery()){if(r.next())perfil=r.getLong(1);if(r.next())throw new IllegalStateException("Perfiles duplicados; se revirtió la restauración");}}
                if(perfil==null){
                    try(var s=c.prepareStatement("insert into public.usuario(nombres,correo,password_hash,id_supabase,estado,id_rol,debe_cambiar_password) select 'Administrador',?,null,?,'ACTIVO',id_rol,false from public.rol where upper(nombre_rol)='ADMINISTRADOR'")){s.setString(1,correo);s.setObject(2,sujeto);if(s.executeUpdate()!=1)throw new IllegalStateException("No se encontró un único rol administrador");}
                }else{
                    try(var s=c.prepareStatement("update public.usuario set id_supabase=?,password_hash=null,estado='ACTIVO',id_rol=(select id_rol from public.rol where upper(nombre_rol)='ADMINISTRADOR'),debe_cambiar_password=false where id_usuario=?")){s.setObject(1,sujeto);s.setLong(2,perfil);s.executeUpdate();}
                }
                c.commit();
                try(var s=c.createStatement();var r=s.executeQuery("select r.nombre_rol,count(rp.id_permiso) permisos from public.rol r left join public.rol_permiso rp using(id_rol) where r.nombre_rol in ('ADMINISTRADOR','PROJECT MANAGER','ANALISTA','Cliente') group by r.id_rol order by r.nombre_rol")){while(r.next())System.out.println(r.getString(1)+": "+r.getInt(2)+" permisos");}
                System.out.println("Perfil administrador activo y vinculado. No se muestra la contraseña ni el hash.");
            }catch(Exception e){c.rollback();if(e instanceof SQLException s)System.err.println("Restauración revertida. SQLSTATE="+s.getSQLState());else System.err.println("Restauración revertida: "+e.getMessage());System.exit(1);}
        }catch(SQLException e){System.err.println("No se pudo conectar. SQLSTATE="+e.getSQLState());System.exit(1);}
    }
}
