package com.plc.cachelock;

import com.plc.annotation.CacheLock;
import com.plc.annotation.LockedComplexObject;
import com.plc.annotation.LockedObject;
import com.plc.exception.CacheLockException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Aspect
@Order(10)
public class CacheLockAop {
    private Logger logger = LoggerFactory.getLogger(CacheLockAop.class);

    private RedisTemplate redisTemplate;

    public static int ERROR_COUNT = 0;

    public CacheLockAop(RedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    @Pointcut(value = "@annotation(com.plc.annotation.CacheLock)")
    private void cutCacheLock() {

    }

    @Around("cutCacheLock()")
    public Object doCutCacheLock(ProceedingJoinPoint point) throws Throwable {
        MethodSignature ms = (MethodSignature) point.getSignature();
        Method method = ms.getMethod();
        CacheLock cacheLock = method.getAnnotation(CacheLock.class);
        if (null == cacheLock) {
            System.out.println("no cacheLock annotation");
            return point.proceed();
        }
        //获得方法中参数的注解
        Annotation[][] annotations = method.getParameterAnnotations();
        //根据获取到的参数注解和参数列表获得加锁的参数
        Object lockedObject = getLockedObject(annotations,point.getArgs());
        if(lockedObject == null){
            System.err.println("lock field is null");
            return point.proceed();
        }
        String objectValue = lockedObject.toString();
        //新建一个锁
        CacheLockManager lock = new CacheLockManager(redisTemplate,cacheLock.lockedPrefix(), objectValue);
        //加锁
        boolean result = lock.lock(cacheLock.timeOut(), cacheLock.expireTime());
        if(!result){//取锁失败
            ERROR_COUNT += 1;
            throw new CacheLockException("get lock fail");
        }
        try{
            //加锁成功，执行方法
            //执行业务
            return point.proceed();
        }finally{
            lock.unlock();//释放锁
        }
    }

    /**
     *
     * @param annotations
     * @param args
     * @return
     * @throws CacheLockException
     */
    private Object getLockedObject(Annotation[][] annotations,Object[] args) throws CacheLockException{
        if(null == args || args.length == 0){
            throw new CacheLockException("方法参数为空，没有被锁定的对象");
        }

        if(null == annotations || annotations.length == 0){
            throw new CacheLockException("没有被注解的参数");
        }
        //不支持多个参数加锁，只支持第一个注解为lockedObject或者lockedComplexObject的参数
        int index = -1;//标记参数的位置指针
        for(int i = 0;i < annotations.length;i++){
            for(int j = 0;j < annotations[i].length;j++){
                if(annotations[i][j] instanceof LockedComplexObject){//注解为LockedComplexObject
                    index = i;
                    try {
                        Class<?> aClass = args[i].getClass();
                        PropertyDescriptor pd = new PropertyDescriptor(((LockedComplexObject)annotations[i][j]).field(), aClass);
                        Method readMethod = pd.getReadMethod();
                        return readMethod.invoke(args[i]);
                    } catch (SecurityException e) {
                        throw new CacheLockException("注解对象中没有该属性" + ((LockedComplexObject)annotations[i][j]).field());
                    }catch (IntrospectionException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
                if(annotations[i][j] instanceof LockedObject){
                    index = i;
                    break;
                }
            }
            //找到第一个后直接break，不支持多参数加锁
            if(index != -1){
                break;
            }
        }

        if(index == -1){
            throw new CacheLockException("请指定被锁定参数");
        }

        return args[index];
    }

}
