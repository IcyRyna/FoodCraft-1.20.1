package org.foodcraft.test;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.foodcraft.FoodCraft;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** GPU rendering of every item context, block state/view, and discrete Minecraft light pair. */
public final class VisualMatrixVerification {
    private record Subject(String id,ItemStack item,BlockState state,int view){}
    private static final List<Subject> subjects=new ArrayList<>();
    private static int subjectIndex,lightIndex,sheetIndex,tile,frames;
    private static TextureTarget target;
    private static NativeImage sheet;
    private static BufferedWriter metrics;
    private static Path directory;
    private static final int SIZE=64,COLS=12,ROWS=10;
    private VisualMatrixVerification(){}
    public static boolean tick(Minecraft mc){
        if(target==null)initialize();
        // Bounded work per client tick preserves packet processing while the matrix runs.
        for(int budget=0;budget<64&&subjectIndex<subjects.size();budget++){
            var subject=subjects.get(subjectIndex);int block=lightIndex&15,sky=lightIndex>>>4;
            try(var frame=render(mc,subject,LightTexture.pack(block,sky))){
                int pixels=0,missing=0;long energy=0;
                for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
                    int rgba=frame.getPixelRGBA(x,y),alpha=rgba>>>24;
                    if(alpha==0)continue;pixels++;int r=rgba&255,g=rgba>>>8&255,b=rgba>>>16&255;energy+=r+g+b;
                    if(r>220&&b>220&&g<30)missing++;
                }
                // Missing-model checks are also made against baked-model identities by UiDetailVerification.
                metrics.write(subject.id+","+subject.view+","+block+","+sky+","+pixels+","+energy+","+missing+"\n");frames++;
                if(lightIndex==255){
                    for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)sheet.setPixelRGBA((tile%COLS)*SIZE+x,(tile/COLS)*SIZE+y,frame.getPixelRGBA(x,y));
                    Files.writeString(directory.resolve("contact-index.csv"),sheetIndex+","+tile+","+subject.id+","+subject.view+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                    if(++tile==COLS*ROWS)saveSheet();
                }
            }catch(IOException failure){throw new UncheckedIOException(failure);}
            if(++lightIndex==256){lightIndex=0;subjectIndex++;}
        }
        if(subjectIndex<subjects.size())return false;
        try{
            if(tile>0)saveSheet();metrics.close();target.destroyBuffers();
            Files.writeString(directory.resolve("visual-complete.txt"),"subjects="+subjects.size()+" light_pairs=256 rendered_frames="+frames+"\n");
        }catch(IOException failure){throw new UncheckedIOException(failure);}
        System.out.println("FOODCRAFT VISUAL MATRIX PASS subjects="+subjects.size()+" light_pairs=256 frames="+frames+" gpu=true");return true;
    }
    private static void initialize(){
        String selection=System.getProperty("foodcraft.qa.visual.filter","");Set<String> filter=selection.isEmpty()?Set.of():Set.of(selection.split(","));
        FoodCraft.ITEMS.forEach((id,item)->{if(filter.isEmpty()||filter.contains(id))for(var context:ItemDisplayContext.values())subjects.add(new Subject("item/"+id,new ItemStack(item),null,context.ordinal()));});
        FoodCraft.BLOCKS.forEach((id,block)->{if(!filter.isEmpty()&&!filter.contains(id))return;int state=0;for(var value:block.getStateDefinition().getPossibleStates()){for(int view=0;view<6;view++)subjects.add(new Subject("block/"+id+"/"+state,null,value,view));state++;}});
        if(subjects.isEmpty())throw new IllegalArgumentException("No visual subjects matched the requested IDs");
        directory=Path.of(System.getProperty("foodcraft.evidence.dir")).resolve("visual-matrix");
        try{
            Files.createDirectories(directory);metrics=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(directory.resolve("light-metrics.csv.gz"))),java.nio.charset.StandardCharsets.UTF_8));
            metrics.write("subject,view,block_light,sky_light,pixels,rgb_sum,magenta_pixels\n");
        }catch(IOException failure){throw new UncheckedIOException(failure);}
        target=new TextureTarget(SIZE,SIZE,true,Minecraft.ON_OSX);sheet=new NativeImage(COLS*SIZE,ROWS*SIZE,true);
    }
    private static NativeImage render(Minecraft mc,Subject subject,int light){
        RenderSystem.backupProjectionMatrix();var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();RenderSystem.applyModelViewMatrix();
        try{
            target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(-1.7f,1.7f,-1.7f,1.7f,-10,10),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.enableDepthTest();RenderSystem.setShaderColor(1,1,1,1);com.mojang.blaze3d.platform.Lighting.setupFor3DItems();mc.gameRenderer.lightTexture().turnOnLightLayer();
            var pose=new PoseStack();var buffers=mc.renderBuffers().bufferSource();
            if(subject.state!=null){
                float yaw=subject.view<4?subject.view*90+35:45,pitch=subject.view<4?25:subject.view==4?70:-70;
                pose.mulPose(Axis.XP.rotationDegrees(pitch));pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.translate(-0.5,-0.5,-0.5);
                mc.getBlockRenderer().renderSingleBlock(subject.state,pose,buffers,light,OverlayTexture.NO_OVERLAY);
            }else mc.getItemRenderer().renderStatic(subject.item,ItemDisplayContext.values()[subject.view],light,OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);
            buffers.endBatch();return Screenshot.takeScreenshot(target);
        }finally{
            target.unbindWrite();mc.getMainRenderTarget().bindWrite(true);RenderSystem.restoreProjectionMatrix();model.popPose();RenderSystem.applyModelViewMatrix();
        }
    }
    private static void saveSheet()throws IOException{
        sheet.writeToFile(directory.resolve(String.format(Locale.ROOT,"contact-%03d.png",sheetIndex++)));sheet.close();sheet=new NativeImage(COLS*SIZE,ROWS*SIZE,true);tile=0;
    }
}
