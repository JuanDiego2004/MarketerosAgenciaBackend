import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

// Reutiliza el esquema sintético de CrmMockarooServicio; nunca envía datos del CRM.
const config = {...process.env};
try {
  for (const linea of (await readFile(new URL("../.env",import.meta.url),"utf8")).split(/\r?\n/)) {
    if (!linea.trim() || linea.trimStart().startsWith("#") || !linea.includes("=")) continue;
    const n = linea.indexOf("="), clave = linea.slice(0,n).trim();
    let valor = linea.slice(n+1).trim();
    if (/^(["']).*\1$/.test(valor)) valor = valor.slice(1,-1);
    config[clave] ??= valor;
  }
} catch (e) { if (e.code !== "ENOENT") throw e; }
if (config.CRM_MOCKAROO_HABILITADO !== "true" || !config.CRM_MOCKAROO_API_KEY) {
  console.error("Mockaroo no está configurado en el entorno local. No se inventaron estadísticas.");
  process.exit(1);
}
const numero = (name,min,max) => ({name,type:"Number",min,max,decimals:0});
const campos = [{name:"n",type:"Row Number"},numero("impresiones",1000,10000),numero("ctr_base",100,600),numero("conversion_base",100,1000),numero("costo_centavos",15,250),numero("ticket_centavos",2000,25000)];
const respuesta = await fetch("https://api.mockaroo.com/api/generate.json?count=900&array=true", {
  method:"POST",headers:{"X-API-Key":config.CRM_MOCKAROO_API_KEY,"Content-Type":"application/json","User-Agent":"MarketingSystem/1.0"},body:JSON.stringify(campos),signal:AbortSignal.timeout(45000),
}).catch(() => null);
if (!respuesta?.ok) {
  console.error(`Mockaroo no devolvió estadísticas válidas${respuesta ? ` (HTTP ${respuesta.status})` : ""}. No se guardó un archivo parcial.`);
  process.exit(1);
}
const datos = await respuesta.json();
if (!Array.isArray(datos) || datos.length !== 900) throw new Error("Mockaroo devolvió un lote incompleto");
const paises = ["PE","CO","MX","ES","US","BR","JP","ZA","AU"], vistos = new Set();
function natural(f,campo,min,max) {
  if (!/^\d{1,9}$/.test(String(f[campo]))) throw new Error(`Número inválido en ${campo}`);
  const v=Number(f[campo]);
  if (v<min || v>max) throw new Error(`Valor fuera de rango en ${campo}`);
  return v;
}
const filas=datos.map((f) => {
  const n=natural(f,"n",1,900);
  if (vistos.has(n)) throw new Error("Filas duplicadas de Mockaroo");
  vistos.add(n);
  const impresiones=natural(f,"impresiones",1000,10000),clics=Math.round(impresiones*natural(f,"ctr_base",100,600)/10000),conversiones=Math.round(clics*natural(f,"conversion_base",100,1000)/10000);
  const inversion=(clics*natural(f,"costo_centavos",15,250)/100).toFixed(2),ingresos=(conversiones*natural(f,"ticket_centavos",2000,25000)/100).toFixed(2);
  return {n,sql:`(${Math.floor((n-1)/90)+1},${Math.floor((n-1)%90/9)},'${paises[(n-1)%9]}',${impresiones},${clics},${conversiones},${inversion},${ingresos})`};
}).sort((a,b)=>a.n-b.n);
const fecha=new Intl.DateTimeFormat("en-CA",{timeZone:"America/Bogota",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) throw new Error("Fecha de lote inválida");
const sql=`-- Estadísticas sintéticas descargadas de Mockaroo. Sin identificadores ni datos de clientes.\n-- 10 anuncios x 10 días x 9 países. Importes simulados en PEN.\nCREATE TEMP TABLE demo_contexto(fecha date) ON COMMIT DROP;\nINSERT INTO demo_contexto VALUES('${fecha}');\nCREATE TEMP TABLE demo_estadisticas(lote int,dia int,pais text,impresiones bigint,clics bigint,conversiones bigint,inversion numeric,ingresos numeric,PRIMARY KEY(lote,dia,pais)) ON COMMIT DROP;\nINSERT INTO demo_estadisticas VALUES\n${filas.map((f)=>f.sql).join(",\n")};\n`;
const destino=new URL("./metricas-demo-mockaroo.sql",import.meta.url);
await writeFile(destino,sql,"utf8");
console.log(`Mockaroo: 900 filas sintéticas validadas. Archivo: ${fileURLToPath(destino)}`);
