package com.wondroid.swprovider

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.w3c.dom.Document
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import java.text.DecimalFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SwProviderPlugin implements Plugin<Project> {

    private static final String HEAD = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";//头部
    private static final String START_TAG = "<resources>\n";//开始标签
    private static final String END_TAG = "</resources>\n";//结束标签


    private static final float DP_BASE = 360//360dp为基准

    private static final int DP_MAX = 720//所有dimens文件dp从0生成到这个值
    private static final int SP_MAX = 48//SP最大

    private static final int[] dps = [360, 384, 392, 400, 410, 411, 480, 533, 592,
                                      600, 640, 662, 720, 768, 800, 811, 820, 900, 960, 961, 1024, 1280]//常见dp列表
//    private static final int[] dps = [100,481,510,720,900]//常见dp列表

//    private static final int[] dps = [360, 410]

    private static String rootPath = "";//生成文件的主目录

    private static ExecutorService fixedThreadPool;//线程池，用于生成XML文件
    private static int size_thread = 5;//线程池大小


    private static DocumentBuilderFactory dbFactory;
    private static DocumentBuilder db;
    private static Document document;

    private static Project project


    @Override
    void apply(Project project) {
//        project.extensions.create("swProvider")
        this.project = project
        ArrayList<String> sourceSets = project.extensions.android.sourceSets.main.res.srcDirs
        project.logger.error("资源文件路径列表:" + sourceSets)

//        sourceSets.each { temp ->
//            project.logger.error("temp的值是" + temp + "《》类型是:" + temp.class)
////            rootPath = temp
////            project.logger.error("测试")
////            String root = temp +"/values/dimens.xml"
//
//        }

        sourceSets.each { temp ->
            rootPath = temp.getPath()
            String root = temp.getPath() + "/values/dimens.xml"
            project.logger.error("即将处理该资源路径:" + root)
            if (!isLegalFile(root)) {
                project.logger.error("该路径下没有找到dimens.xml文件， path = " + root)
            } else {
                project.logger.error("找到该路径下的dimens.xml文件， path = " + root)


                dbFactory = DocumentBuilderFactory.newInstance();
                db = dbFactory.newDocumentBuilder();
                //将给定 URI 的内容解析为一个 XML 文档,并返回Document对象
                //记得改成自己当前项目的路径
                document = db.parse(root);
                NodeList dimenList = document.getElementsByTagName("dimen")

                if (dimenList.getLength() == 0) {
                    project.logger.error("该文件中没有dimen标签" + root)
                } else {
                    project.logger.error("该文件中dimen标签数量:" + dimenList.getLength())
                    List<Dimen> list = new ArrayList<>()
                    dimenList.each { node ->
                        //获取第i个dimen的所有属性
                        NamedNodeMap namedNodeMap = node.getAttributes();
                        //获取已知名为name的属性值
                        String atrName = namedNodeMap.getNamedItem("name").getTextContent();

                        String value = node.getTextContent();

                        project.logger.error("+++atrName++++++++++++++++++++" + atrName);
                        project.logger.error("+++++++++++++value++++++++++" + value);


                        list.add(new Dimen(atrName, value));
                    }

                    fixedThreadPool = Executors.newFixedThreadPool(size_thread);
                    dps.eachWithIndex { node, index ->
                        XMLThread xmlThread = new XMLThread(index, list);
                        fixedThreadPool.execute(xmlThread);//线程启动执行
                    }
                }
            }
        }

    }

    public static boolean isLegalFile(String path) {
        if (path == null) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isFile() && file.length() > 0;
    }

    private static class Dimen {
        private String atrName;
        private String value;

        public Dimen(String atrName, String value) {
            this.atrName = atrName;
            this.value = value;
        }

        public String getAtrName() {
            return atrName;
        }

        public void setAtrName(String atrName) {
            this.atrName = atrName;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }


    private static class XMLThread implements Runnable {

        private int index = 0;
        private List<Dimen> list;

        public XMLThread(int index, List<Dimen> list) {
            this.index = index;
            this.list = list;
        }

        @Override
        public void run() {
            //记得改成自己当前项目的路径
            project.logger.error(rootPath + "/values-sw" + dps[index] + "dp/")
            generateXMl(list, index, rootPath + "/values-sw" + dps[index] + "dp/", "dimens.xml");
        }
    }

    private static void generateXMl(List<Dimen> list, int index, String pathDir, String fileName) {
        try {
            File diectoryFile = new File(pathDir);
            if (!diectoryFile.exists()) {
                diectoryFile.mkdirs();
            }
            File file = new File(pathDir + fileName);
            if (file.exists()) {
                file.delete();
            }
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(HEAD);
            fileWriter.write(START_TAG);

            //?????????????????????????????????????????????
            int size = list.size();
            String atrName;
            String value;
            for (int i = 0; i < size; i++) {
                atrName = list.get(i).getAtrName();
                value = list.get(i).getValue();

                String output = "\t<dimen name=\"" + atrName + "\">" +
                        roundString(Float.valueOf(value.substring(0, value.length() - 2)), index) +
                        value.substring(value.length() - 2) + "</dimen>\n";
                fileWriter.write(output);

            }

            fileWriter.write(END_TAG);
            fileWriter.flush();
            fileWriter.close();

            project.logger.error("写入成功");
        } catch (IOException e) {
            e.printStackTrace();
            project.logger.error("写入失败,reason = " + e.getMessage());


        }
    }

    //精确到小数点后2位,并且四舍五入(因为有SW1280dp,基准是160dp，1dp=1px,
    // 如果精确到小数点后一位，四舍五入会有0.5dp误差，在sw1280dp中会有4PX误差，精确到小数点后2位，四舍五入，误差控制在1PX之内)
    private static String roundString(float data, int index) {
        String result = "";
        float floatResult = data * dps[index] / DP_BASE;
        DecimalFormat df = new DecimalFormat("0.00");
        result = df.format(floatResult);
        return result;
    }
}