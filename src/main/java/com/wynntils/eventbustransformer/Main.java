package com.wynntils.eventbustransformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.ServiceLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.eventbus.EventBusEngine;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import net.minecraftforge.eventbus.IEventBusEngine;

public class Main {

    static {
        System.setProperty("org.apache.logging.log4j.level", "INFO");
    }

    private static final Logger LOGGER = LogManager.getLogger("EventBusTransformer");

    public static void main(String[] args) throws ZipException, IOException {
        File file = new File(args[0]);
        ZipFile zip = new ZipFile(file);
        File transformed = new File(args.length > 1 ? args[1] : "transformed.jar");
        ZipOutputStream output = new ZipOutputStream(new FileOutputStream(transformed));
        IEventBusEngine engine = ServiceLoader.load(IEventBusEngine.class).findFirst()
                .orElseGet(() -> new EventBusEngine()); // if not loaded as a module

        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry next = entries.nextElement();
            output.putNextEntry(next);
            byte[] content = IOUtils.toByteArray(zip.getInputStream(next));
            if (next.getName().endsWith(".class")) {
                Type type = Type.getObjectType(next.getName().replace(".class", ""));
                String className = type.getClassName();
                if (engine.handlesClass(type) && className.startsWith("com.wynntils")) {
                    LOGGER.info("Transforming class: " + next.getName());
                    ClassReader reader = new ClassReader(content);
                    ClassNode node = new ClassNode();
                    reader.accept(node, 0);

                    int flags = engine.processClass(node, type);

                    ClassWriter writer = new ClassWriter(flags);
                    node.accept(writer);

                    content = writer.toByteArray();
                }
            }

            IOUtils.write(content, output);
            output.closeEntry();
        }
        output.close();
        zip.close();

        if (args.length <= 1) {
            FileOutputStream write = new FileOutputStream(file);
            FileInputStream read = new FileInputStream(transformed);
            IOUtils.copy(read, write);
            write.close();
            read.close();
        }
    }
}
