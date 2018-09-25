package com.mymvc.mvc.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mymvc.mvc.annotation.JTAutowired;
import com.mymvc.mvc.annotation.JTController;
import com.mymvc.mvc.annotation.JTRequestMapping;
import com.mymvc.mvc.annotation.JTRequestParam;
import com.mymvc.mvc.annotation.JTService;
/**
 * 视频看到48分钟
 * @author Dell
 *
 */
public class JTDispatcherServlet extends HttpServlet{
	private Properties p=new Properties();
	private List<String> classNames=new ArrayList<String>();
	private Map<String,Object> ioc=new HashMap<String,Object>();
	//private Map<String,Method> handlerMapping=new ConcurrentHashMap<String,Method>();
	private List<Handler> handlerMapping=new ArrayList<JTDispatcherServlet.Handler>();

	@Override
	public void init(ServletConfig config) throws ServletException {
		System.out.println("------正在加载springmvc------------");
		//1.加载配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		//2.初始化所有的向关联的类,扫描用户设定的包下面的所有的类
		doScanner(p.getProperty("scanPackage"));
		//<bean id="xxx" class="xxx">
		//3.把这些扫描到的类通过反射机制来实现初始化,并且放到IOC容器之中(Map beanName)
		doInstance();

		//4.实现依赖注入
		//给加上@JTAutowired注解的字段,哪怕是私有的也要赋值
		doAutowired();
		//5.初始化HandlerMapping
		//将url和method关联起来
		initHandlerMapping();
		System.out.println("------加载成功------------");
		//等待doGet或者doPost方法
		
	}
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			doDispatch(req,resp);
		}catch(Exception e) {
			resp.getWriter().write("500 Exception,Details : \r\n"+Arrays.toString(e.getStackTrace()));
			e.printStackTrace();
		}
	}
	protected void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {	
		try {
			Handler handler=getHandler(req);
			if(handler==null) {
			//如果没有匹配上，返回404
				resp.getWriter().write("404 Not Found");
				return;
			}
			//获取方法的参数列表
			Class<?> [] paramTypes=handler.method.getParameterTypes();
			//保存所有需要自动复制的参数值
			Object[] paramValues=new Object[paramTypes.length];
			Map<String,String[]> params=req.getParameterMap();
			for (Entry<String,String[]> param : params.entrySet()) {
				String value=Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
				
				//如果找到匹配的对象，则开始填充充参数值
				if(!handler.paramIndexMapping.containsKey(param.getKey())) {continue;}
				int index=handler.paramIndexMapping.get(param.getKey());
				paramValues[index]=covert(paramTypes[index],value);
			}
			//设置方法中的request和response对象
			int reqIndex=handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramValues[reqIndex]=req;
			int resIndex=handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramValues[resIndex]=resp;
			
			handler.method.invoke(handler.controller, paramValues);
		} catch (Exception e) {
			e.printStackTrace();
			
		}
	}
	private Object covert(Class<?> type,String value) {
		if(Integer.class==type) {
			return Integer.valueOf(value);
		}
		return value;
	}
	private void doLoadConfig(String location) {
		InputStream is=this.getClass().getClassLoader().getResourceAsStream(location);
		try {
			p.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(null!=is) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}}
		}
	}
	private void doScanner(String packageName) {
		URL url=this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.", "/"));
		File dir=new File(url.getFile());
		for(File file:dir.listFiles()) {
			if(file.isDirectory()) {
				doScanner(packageName+"."+file.getName());

			}else {
				String className=packageName+"."+file.getName().replaceAll(".class", "");
				classNames.add(className);
			}
		}
	}
	private void doInstance() {
		if(classNames.isEmpty()) {return;}
		for (String className : classNames) {
			try {
				Class<?> clazz=Class.forName(className);
				//用反射来实现实例化,不是所有的类都要实例化
				if(clazz.isAnnotationPresent(JTController.class)) {
					String beanName=lowerFirst(clazz.getSimpleName());
					ioc.put(beanName, clazz.newInstance());

				}
				else if(clazz.isAnnotationPresent(JTService.class)) {



					//1.如果自己设置了一个名字,就要用自己的名字优先
					JTService service=	clazz.getAnnotation(JTService.class);
					String beanName=service.value();
					//2.如果自己没有设置名字,默认首字母小写
					if(!"".equals(beanName)) {
						beanName=lowerFirst(clazz.getSimpleName());
					}
					Object instance=clazz.newInstance();
					ioc.put(beanName, instance);
					//3.如果@Autowired标注的是一个接口的话,默认将其实现类的是你注入进来
					Class<?>[]  interfaces=clazz.getInterfaces();
					for (Class<?> i : interfaces) {
						ioc.put(i.getName(), instance);
					}
				}
				else {
					continue;
				}
			}
			catch(Exception e) {
				e.printStackTrace();
				continue;
			}
		}

	}
	private void doAutowired() {
		if(ioc.isEmpty()) {return;}
		for (Entry<String,Object> entry : ioc.entrySet()) {
			//扫描所有的字段,看有没有autowired的注解
			Field[] fields=entry.getValue().getClass().getDeclaredFields();
			for (Field field : fields) {
				if(!field.isAnnotationPresent(JTAutowired.class)) {continue;}
				JTAutowired autowired=field.getAnnotation(JTAutowired.class);
				String beanName=autowired.value();
				if("".equals(beanName)) {
					beanName=field.getType().getName();
				}
				//强吻
				field.setAccessible(true);
				try {
					field.set(entry.getValue(),ioc.get(beanName));
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		}

	}
	private void initHandlerMapping(){
		if(ioc.isEmpty()) {return;}
		for (Entry<String,Object> entry : ioc.entrySet()) {
			Class<?> clazz=entry.getValue().getClass();
			if(!clazz.isAnnotationPresent(JTController.class)) {continue;}
			String baseUrl="";
			if(clazz.isAnnotationPresent(JTRequestMapping.class)) {
				JTRequestMapping requestMapping=clazz.getAnnotation(JTRequestMapping.class);
				baseUrl=requestMapping.value();

			}
			Method[] methods=clazz.getMethods();
			for (Method method : methods) {
				if(!method.isAnnotationPresent(JTRequestMapping.class)) {continue;}
				JTRequestMapping requestMapping=method.getAnnotation(JTRequestMapping.class);
				//String url=requestMapping.value();
				//url=(baseUrl+"/"+url).replaceAll("/+", "/");
				//handlerMapping.put(url, method);
				String regex=("/"+baseUrl+requestMapping.value()).replaceAll("/+","/");
				Pattern pattern=Pattern.compile(regex);
				handlerMapping.add(new Handler(pattern,entry.getValue(),method));
				System.out.println("Mapping :"+regex+","+method);

			}

		}
	}
	/**
	 * 实现首字母小写
	 * @param name
	 * @return
	 */
	private String lowerFirst(String name ) {
		char[] chars=name.toCharArray();
		chars[0]+=32;
		return String.valueOf(chars);

	}
	private Handler getHandler(HttpServletRequest req) throws Exception{
		if(handlerMapping.isEmpty()) {return null;}
		String url=req.getRequestURI();
		String contextPath=req.getContextPath();
		url=url.replace(contextPath, "").replaceAll("/+", "/");
		for (Handler handler : handlerMapping) {
			try {
				Matcher matcher=handler.pattern.matcher(url);
				//如果没有匹配上
				if(!matcher.matches()) {continue;}
				return handler;
			} catch (Exception e) {
				throw e;
			}
		}
		return null;
	}
	private class Handler{
		protected Object controller;//保存方法对应的实例
		protected Method method;//保存映射的方法
		protected Pattern pattern;
		protected Map<String,Integer> paramIndexMapping; //参数顺序
		public Handler(Pattern pattern,Object controller,Method method) {
			this.controller = controller;
			this.method = method;
			this.pattern = pattern;
			paramIndexMapping=new HashMap<String,Integer>();
			putParamIndexMapping(method);
		}
		private void putParamIndexMapping(Method method) {
			//提取方法中加入了注解的参数
			Annotation [][] pa=method.getParameterAnnotations();
			for(int i=0;i<pa.length;i++) {
				for(Annotation a:pa[i]) {
					if(a instanceof JTRequestParam) {
						String paramName=((JTRequestParam)a).value();
						if(!"".equals(paramName.trim())) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			//提取方法中的request和response参数
			Class<?> [] paramTypes=method.getParameterTypes();
			for(int i=0;i<paramTypes.length;i++) {
				Class<?> type=paramTypes[i];
				if(type==HttpServletRequest.class||
				type==HttpServletResponse.class){
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}
		
	}
}

