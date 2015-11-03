/**
 * DingDing.com Inc.
 * Copyright (c) 2000-2016 All Rights Reserved.
 */
package com.dingding.open.achelous.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.dingding.open.achelous.core.cache.HierarchicalCache;
import com.dingding.open.achelous.core.parser.CoreConfig;
import com.dingding.open.achelous.core.parser.Parser;
import com.dingding.open.achelous.core.parser.properties.PropertiesParser;
import com.dingding.open.achelous.core.pipeline.DftPipeline;
import com.dingding.open.achelous.core.pipeline.Pipeline;
import com.dingding.open.achelous.core.plugin.Plugin;
import com.dingding.open.achelous.core.plugin.PluginName;
import com.dingding.open.achelous.core.support.Context;
import com.dingding.open.achelous.core.support.PluginMeta;
import com.dingding.open.achelous.core.support.Suite;

/**
 * pipeline管理器。进行所有pipeline的解析和初始化
 * 
 * @see {@link Pipeline}
 * @author surlymo
 * @date Oct 27, 2015
 */
public class PipelineManager {

    private static final Map<String, Pipeline> pipelinePool = new HashMap<String, Pipeline>();

    private static Parser parser;

    // private static final String DEFAULT_PLUGIN_PATH = "com.dingding.open.achelous.core.plugin.impl";

    private static final Map<String, Plugin> pluginMap = new HashMap<String, Plugin>();

    private static String defaultPipeline = null;

    private static final Map<String, Map<String, Map<String, String>>> suite2Plugin2FeatureMap =
            new HashMap<String, Map<String, Map<String, String>>>();

    private static final List<String> pluginPaths = new ArrayList<String>();

    private static CoreConfig coreConfig = null;

    private static volatile AtomicBoolean defaultPipelineSwitch = new AtomicBoolean(false);

    static {
        coreInit(null);
    }

    private static Pipeline getPipeline(String name) {
        return pipelinePool.get(name);
    }

    /**
     * 进行核心的初始化工作
     */
    private static synchronized void coreInit(String coreConfigFilePath) {

        if (parser == null) {
            parser = new PropertiesParser();
        }

        HierarchicalCache.init();

        // 解析获取各类配置
        coreConfig = parser.parser();

        // 进行全部参数的处理
        globalConfigProcess(coreConfig.getGlobalConfig());

        bagging();
    }

    private static void bagging() {
        for (Suite suite : coreConfig.getSuites()) {
            Map<String, Map<String, String>> pluginsFeatureMap = new HashMap<String, Map<String, String>>();
            Pipeline pipeline = new DftPipeline();
            // 首先将自己pipeline下的所有plugin进行实例化，并灌入pool中去。
            pipelinePool.put(suite.getName(), pipeline);
            List<Plugin> plugins = new ArrayList<Plugin>();

            for (PluginMeta meta : suite.getPluginMetas()) {

                if (pluginMap.get(meta.getPluginName()) == null) {
                    continue;
                }

                Plugin plugin = pluginMap.get(meta.getPluginName()).init(suite.getName());
                plugins.add(plugin);
                pluginsFeatureMap.put(meta.getPluginName(), meta.getFeature2ValueMap());
            }
            pipeline.bagging(plugins);

            if (defaultPipelineSwitch.compareAndSet(false, true)) {
                defaultPipeline = suite.getName();
            }

            suite2Plugin2FeatureMap.put(suite.getName(), pluginsFeatureMap);
        }
    }

    public synchronized static void checkPluginPath(String path) {
        if (pluginPaths.contains(path)) {// 如果存在，则返回
            return;
        }
        // 不存在则开始补充初始化
        pluginPaths.add(path);
        initByPath(path);
        bagging();
    }

    private static void initByPath(String path) {
        String initPath = PipelineManager.class.getClassLoader().getResource("").getPath();
        initPath =
                initPath.substring(0, initPath.lastIndexOf("target") + 7) + "classes"
                        + File.separator;

        File file =
                new File(initPath + path.replace(".", File.separator));

        if (!file.exists()) {
            return;
        }

        String prefix = path;
        for (String str : file.list()) {

            if (str.contains("$")) {
                continue;
            }

            Plugin plugin = null;
            try {
                plugin = (Plugin) Class.forName(prefix + "." + str.split("\\.")[0]).newInstance();
            } catch (InstantiationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            PluginName name = plugin.getClass().getAnnotation(PluginName.class);
            pluginMap.put(name.value(), plugin);
        }
    }

    /**
     * 进行全部参数的初始化
     * 
     * @param globalConfig 全局参数
     */
    private static void globalConfigProcess(Map<String, Object> globalConfig) {

        if (globalConfig.get(CoreConfig.GLOBAL_PLUGIN_PATH) == null) {
            return;
        }

        // initPlugins
        for (String path : (List<String>) globalConfig.get(CoreConfig.GLOBAL_PLUGIN_PATH)) {
            if (path != null) {
                pluginPaths.add(path);
            }
            initByPath(path);
        }
    }

    public static void call(Context context) {
        call(defaultPipeline, context);
    }

    public static void call(String pipeline, Context context) {
        Pipeline pipe = getPipeline(pipeline);
        context.setPipelineName(pipeline);
        context.setPlugin2ConfigMap(suite2Plugin2FeatureMap.get(pipeline));
        pipe.combine(context).call();
    }

}