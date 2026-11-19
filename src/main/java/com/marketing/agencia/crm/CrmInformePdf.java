package com.marketing.agencia.crm;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.io.*;
import java.util.*;

@Component
public class CrmInformePdf {
 public byte[] generar(Map<String,Object> datos,String titulo,String conclusiones,int version)throws IOException{
  try(PDDocument doc=new PDDocument();ByteArrayOutputStream salida=new ByteArrayOutputStream()){
   var texto=new ArrayList<String>();var c=(Map<?,?>)datos.get("contrato");var resumen=(Map<?,?>)datos.get("resumen");
   texto.add(titulo);texto.add("INFORME DE "+c.get("servicio")+" | Version "+version);texto.add("Empresa: "+((Map<?,?>)datos.get("empresa")).get("nombre"));texto.add("Contrato CTR-"+c.get("id")+" | "+c.get("desde")+" a "+c.get("hasta"));texto.add("Datos simulados con Mockaroo. Publicaciones registradas dentro del CRM.");texto.add("");
   texto.add("CONDICIONES ECONOMICAS ("+c.get("moneda")+")");for(String k:List.of("honorarios","produccion","presupuesto"))texto.add(k+": "+c.get(k));texto.add("Gasto publicitario ejecutado del escenario: "+datos.get("gasto"));texto.add("Honorarios y presupuesto no acreditan pagos cobrados.");texto.add("");
   texto.add("RESULTADOS PUBLICITARIOS");if(resumen!=null)for(String k:List.of("impresiones","clics","conversiones","ctr","cpc","cpm","cpa"))texto.add(k+": "+Objects.toString(resumen.get(k),"Sin base de calculo"));texto.add("Los ingresos estimados del proveedor no son ventas ganadas del CRM.");texto.add("");
   if(!"ANALISIS".equals(c.get("servicio"))){texto.add("EJECUCION Y PUBLICACIONES");for(Object o:(List<?>)datos.get("anuncios")){var a=(Map<?,?>)o;texto.add(a.get("codigo")+" | "+a.get("nombre")+" | "+a.get("estado")+" | gasto "+a.get("gasto"));}texto.add("");}
   texto.add("ANALISIS".equals(c.get("servicio"))?"HALLAZGOS Y RECOMENDACIONES":"TRABAJO REALIZADO Y HALLAZGOS");for(Object o:(List<?>)datos.get("trabajos")){var t=(Map<?,?>)o;texto.add(t.get("tipo")+" | "+t.get("nombre")+" | "+t.get("estado"));texto.add(Objects.toString(t.get("descripcion"),""));}
   texto.add("");texto.add("CONCLUSIONES Y PROXIMAS ACCIONES");texto.add(conclusiones);
   PDFont font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);List<String> lineas=new ArrayList<>();
   for(String bloque:texto)for(String linea:bloque.split("\\R",-1)){StringBuilder actual=new StringBuilder();for(int cp:linea.codePoints().toArray()){String ch=new String(Character.toChars(cp));try{font.encode(ch);}catch(IllegalArgumentException e){ch="?";}if(font.getStringWidth(actual+ch)/1000*10>490){lineas.add(actual.toString());actual.setLength(0);}actual.append(ch);}lineas.add(actual.toString());}
   int paginas=Math.max(1,(lineas.size()+42)/43);
   for(int p=0;p<paginas;p++){PDPage page=new PDPage(PDRectangle.A4);doc.addPage(page);try(PDPageContentStream out=new PDPageContentStream(doc,page)){
    out.setNonStrokingColor(new Color(29,78,216));out.addRect(0,780,595,62);out.fill();out.setNonStrokingColor(Color.WHITE);out.beginText();out.setFont(font,18);out.newLineAtOffset(48,804);out.showText("MARKETING | Informe de servicio");out.endText();
    out.setNonStrokingColor(new Color(15,23,42));out.beginText();out.setFont(font,10);out.setLeading(15);out.newLineAtOffset(48,750);for(int i=p*43;i<Math.min(lineas.size(),(p+1)*43);i++){out.showText(lineas.get(i));out.newLine();}out.endText();
    out.beginText();out.setFont(font,9);out.newLineAtOffset(48,48);out.showText("CTR-"+c.get("id")+" | Version "+version+" | Pagina "+(p+1)+" de "+paginas);out.endText();
   }}doc.save(salida);return salida.toByteArray();
  }
 }
}
