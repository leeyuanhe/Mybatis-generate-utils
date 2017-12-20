package main;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.internal.DefaultShellCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Administrator on 2016/10/10.
 */
public class Generater {
    public static void main(String[] args) throws Exception {
        List<String> warnings = new ArrayList<String>();
        boolean overwrite = true;
        File configFile = new File("config/generatorConfig.xml");
        ConfigurationParser cp = new ConfigurationParser(warnings);
        Configuration config = cp.parseConfiguration(configFile);
        DefaultShellCallback callback = new DefaultShellCallback(overwrite);
        MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config, callback, warnings);
        myBatisGenerator.generate(null);

        generateInsertAndUpdateBatch();

        System.out.println("生成完毕！");


    }

    public  static void generateInsertAndUpdateBatch() throws Exception{
        //创建SAXReader对象
        SAXReader reader = new SAXReader();
        //读取文件 转换成Document
        Document document = reader.read(new File(Constant.DES_XML));
        //获取根节点元素对象
        Element root = document.getRootElement();
        //新插入节点
        List<Element> list = root.elements();
        //设置包名
        setPrammeter(document, root);

        //生成批量插入语句
        generateInsertBatch(document, list);
        //生成批量更新语句
        generateUpdateBatch(document, list);

        //将内存中的写入到xml
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("UTF-8");
        XMLWriter writer = new XMLWriter(new FileOutputStream(Constant.DES_XML),format);
        writer.write(document);
        writer.close();



    }

    /**
     * @description 生成批量更新
     * @author hely
     * @date 2017/12/6
     * @param
     */
    private static void generateUpdateBatch(Document document, List<Element> list) {
        
        
        Element updateBathcSql = DocumentHelper.createElement("update");
        updateBathcSql.addAttribute("id","updateBatch");
        updateBathcSql.addAttribute("parameterType","java.util.List");


        Element childElement = DocumentHelper.createElement("foreach");

        childElement.addAttribute("collection","updateList");
        childElement.addAttribute("item","item");
        childElement.addAttribute("index","index");
        childElement.addAttribute("separator",";");

        updateBathcSql.add(childElement);

        String updatepath ="/mapper/update[@id='updateByPrimaryKeySelective']";

        List<Element> updateComposites = document.selectNodes(updatepath);

        for (Element composite : updateComposites) {

            String text = composite.getText();
            String newText = text.substring(0, text.indexOf("where"));
            childElement.addText(newText);
            Element set = DocumentHelper.createElement("set");
            childElement.add(set);
            childElement.addText("\n where Id = #{item.id,jdbcType=INTEGER}");
            Iterator<Element> iterator = composite.elementIterator();

            if (iterator.hasNext()) {
                Element elemCopy = detachElement(iterator);
                Iterator<Element> child_iterator = elemCopy.elementIterator();
                while(child_iterator.hasNext()){
                    Element elecopy = detachElement(child_iterator);
                    editColumnElement(elecopy,elecopy.attribute("test").getName(),"item."+elecopy.attribute("test")
                            .getValue());
                    set.add(elecopy);
                }
            }
        }


        list.add(list.size(),updateBathcSql);
    }

    /**
     * @description 生成批量插入
     * @author hely
     * @date 2017/12/6
     * @param
     */
    private static void generateInsertBatch(Document document, List<Element> list) {

        Element sql = DocumentHelper.createElement("insert");
        //查询insertselective的节点借用
        String xpath ="/mapper/insert[@id='insertSelective']";
        List<Element> composites = document.selectNodes(xpath);

        for (Element composite : composites) {
            //1.1 设置节点属性
            List<Attribute> ablist = composite.attributes();
            //遍历属性节点
            for(Attribute attribute : ablist){

                if (attribute.getName().equals("id")){
                    sql.addAttribute(attribute.getName(),"insertBatch");
                }
                if (attribute.getName().equals("parameterType")){
                    sql.addAttribute(attribute.getName(),"java.util.List");
                }
            }

            sql.addAttribute("useGeneratedKeys","true");
            //1.2设置节点内容
            String text = composite.getText();
            sql.addText(text);

            Iterator<Element> iterator = composite.elementIterator();
            if (iterator.hasNext()) {

                Element elemCopy = detachElement(iterator);

                Iterator<Element> child_iterator = elemCopy.elementIterator();
                while(child_iterator.hasNext()){

                    Element child = child_iterator.next();
                    editColumnElement(child,child.attribute("test").getName(),"insertList.get(0)."+child.attribute("test").getValue());
                }
                sql.add(elemCopy);

                //1.3 改写values后边的节点
                sql.addText("values");

                Element childElement = DocumentHelper.createElement("foreach");
                childElement.addAttribute("collection","insertList");
                childElement.addAttribute("item","model");
                childElement.addAttribute("separator",",");

                sql.add(childElement);

                childElement.setText("(");

                if (iterator.hasNext()){
                    Element ee = iterator.next();
                    Iterator<Element> valueiter = ee.elementIterator();
                    while (valueiter.hasNext()){

                        Element elecopy = detachElement(valueiter);

                        editColumnElement(elecopy,elecopy.attribute("test").getName(),"model."+elecopy.attribute("test").getValue());
                        childElement.add(elecopy);

                        if (!valueiter.hasNext()) {
                            childElement.addText(")");
                        }
                    }
                }
            }
        }


        list.add(list.size(),sql);
    }


    /**
     * @Description 必须把element clone过来才能添加  否则会报已存在的异常
     * @Author heliyuan
     * @Date 2017/4/3 下午10:59
     */
    private static Element detachElement(Iterator<Element> iterator) {
        Element e = iterator.next();
        Element elemCopy = (Element)e.clone();
        elemCopy.detach();
        return elemCopy;
    }

    /**
     * @Description 设置po类和mapper包名
     * @Author heliyuan
     * @Date 2017/4/3 下午6:21
     */
    private static void setPrammeter(Document document, Element root) {

        String namespace = root.attribute("namespace").getValue();
        root.attribute("namespace").setValue(Constant.DES_PACK_MAPPER+namespace);

        String typath = "/mapper/resultMap[@type]";
        List<Element> resultList = document.selectNodes(typath);
        for (Element element : resultList) {
            String type = element.attribute("type").getValue();
            String replace = type.replace("po.", Constant.DES_PACK_PO);
            element.attribute("type").setValue(replace);
        }


        String ypath ="//@parameterType";
        List<Attribute> typeList = document.selectNodes(ypath);

        for (Attribute attr : typeList) {
            String value = attr.getValue();
            if (value.contains("po.")){
                String replace = value.replace("po.", Constant.DES_PACK_PO);
                attr.setValue(replace);
            }

        }
    }

    /**
     * @Description  set字段
     * @Author heliyuan
     * @Date 2017/4/3 下午3:15
     */
    private static Element editColumnElement(Element child, String name, String value) {

        Attribute attribute = child.attribute(name);
        attribute.setValue(value.trim());
        List list = new ArrayList();
        list.add(attribute);
        child.setAttributes(list);

        if (value.contains("model")){
            String childText = child.getText();
            String replace = childText.replace("#{", "#{model.");
            child.setText(replace);

        }

        if (value.contains("item")){
            String childText = child.getText();
            String replace = childText.replace("#{", "#{item.");
            child.setText(replace);

        }

        return child;
    }

/**
 * @Description 打印输出 查看XML结构
 * @Author heliyuan
 * @Date 2017/4/3 下午10:41
 */
    private static void printOut(Element node) {
        System.out.println("当前节点的名称：" + node.getName());
        //首先获取当前节点的所有属性节点
        List<Attribute> list = node.attributes();
        //遍历属性节点
        for(Attribute attribute : list){
            System.out.println("属性"+attribute.getName() +":" + attribute.getValue());
        }
        //如果当前节点内容不为空，则输出
        if(!(node.getTextTrim().equals(""))){
            System.out.println( node.getName() + "：" + node.getText());
        }
    }

}



