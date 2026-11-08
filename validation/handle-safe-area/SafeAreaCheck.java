package dev.edgeflow;
import java.util.Arrays;
import java.util.Random;
public final class SafeAreaCheck {
 static int checks;
 static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 static void full(GestureHandleConfig.Area area,String id){int[] b=GestureHandleConfig.bounds(id,12.3f,0,1,3.5f,area);check(b[1]==area.top&&b[1]+b[3]==area.bottom,"100% excludes system bars "+id+" "+area);check(b[0]==(GestureHandleConfig.isLeft(id)?area.handleEdge(true):area.handleEdge(false)-b[2]),"physical landscape or safe portrait edge "+id);}
 public static void main(String[] args){
  GestureHandleConfig.Area portrait=GestureHandleConfig.safeArea(1264,2780,0,140,0,154);
  check(portrait.top==140&&portrait.bottom==2626&&portrait.height()==2486,"actual OnePlus safe geometry");
  GestureHandleConfig.Area[] rotations={portrait,GestureHandleConfig.safeArea(2780,1264,140,0,154,0),GestureHandleConfig.safeArea(1264,2780,0,140,0,294),GestureHandleConfig.safeArea(2780,1264,154,140,140,0)};
  for(GestureHandleConfig.Area area:rotations)for(String id:GestureHandleConfig.IDS)full(area,id);
  for(int rotation=0;rotation<4;rotation++){int[][] cutouts={{0,140,0,0},{140,0,0,0},{0,0,0,140},{0,0,140,0}};int[] c=cutouts[rotation];check(Arrays.equals(new int[]{0,140,0,0},HandleDisplayArea.unrotate(c[0],c[1],c[2],c[3],rotation)),"upright cutout reference rotation "+rotation);}
  float[] range=GestureHandleConfig.migrateFractions(.04987502f,.950125f,portrait);check(range[0]==0&&range[1]==1,"current accepted region clips to exact 100% safe area");
  range=GestureHandleConfig.migrateFractions(0,1,portrait);check(range[0]==0&&range[1]==1,"legacy full display maps to safe full region");
  Random random=new Random(17);
  for(int i=0;i<250;i++){
   float start=random.nextFloat()*.8f,end=Math.min(1,start+.05f+random.nextFloat()*(.95f-start));range=GestureHandleConfig.migrateFractions(start,end,portrait);
   int oldTop=Math.round(start*2780),oldBottom=Math.min(2780,oldTop+Math.round((end-start)*2780));int[] migrated=GestureHandleConfig.bounds("left",12.3f,range[0],range[1],3.5f,portrait);
   int wantedTop=Math.max(140,oldTop),wantedBottom=Math.min(2626,oldBottom);
   if(wantedBottom-wantedTop>=Math.ceil(portrait.height()*.05f))check(Math.abs(migrated[1]-wantedTop)<=1&&Math.abs(migrated[1]+migrated[3]-wantedBottom)<=1,"legacy physical endpoints preserved within one pixel "+i);
   check(migrated[1]>=140&&migrated[1]+migrated[3]<=2626,"migration never expands into system bars "+i);
   for(GestureHandleConfig.Area area:rotations){int[] a=GestureHandleConfig.bounds("left",12.3f,start,end,3.5f,area),b=GestureHandleConfig.bounds("right",12.3f,start,end,3.5f,area);check(a[1]==b[1]&&a[3]==b[3]&&a[0]==(area.landscape()?0:area.left)&&b[0]+b[2]==(area.landscape()?area.displayWidth:area.right),"mirrored bounds share safe percentage "+i);}
  }
  for(float[] invalid:new float[][]{{Float.NaN,Float.NaN},{2,-1},{-.5f,.01f},{.99f,1},{0,.05f}}){range=GestureHandleConfig.migrateFractions(invalid[0],invalid[1],portrait);check(Float.isFinite(range[0])&&range[0]>=0&&range[1]<=1&&range[1]-range[0]>=.05f-1e-6f,"invalid/clipped range expands inward safely");}
  full(GestureHandleConfig.safeArea(2,1,500,500,500,500),"right");
  System.out.println("PASS "+checks+" safe-area geometry assertions");
 }
}
