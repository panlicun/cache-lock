# cache-lock（分布式锁）

目前版本暂时只支持springboot项目使用，因为内部使用了spring框架的 `spring-boot-starter-data-redis` jar包

目前只支持redis缓存。

###使用方法



1. 需要在项目中引入jar包



2. 将 `CacheLockAop` 类注入到项目中，传入使用的缓存对象，目前只支持redis缓存，故使用spring的RedisTemplate对象。例如
	
		@Bean
		public CacheLockAop cacheLockAop(RedisTemplate redisTemplate){
		    return new CacheLockAop(redisTemplate);
		}

3. 在要加锁的方法中添加如下注解


		@CacheLock(lockedPrefix="test",timeOut=2000,expireTime=1000)


	- lockedPrefix：redis锁key的前缀  默认为""
	- timeOut：等待时间   默认为2秒
	- expireTime：key的过期时间  默认为1秒

4.	在参数上添加如下注解
	
		@LockedComplexObject 或 @LockedObject 注解

	- 	@LockedObject：参数级的注解，用于注解商品ID等基本类型的参数
			
			@CacheLock(lockedPrefix="test",timeOut=2000,expireTime=1000)
			public void test(@LockedObject int id) {
				
			}

	- 	@LockedComplexObject：参数级的注解，用于注解自定义类型的参数（ 如一个商品对象的商品ID ）

			@CacheLock(lockedPrefix="test",timeOut=2000,expireTime=1000)
			public void test(@LockedComplexObject(field="id") Goods goods) {
				
			}




----------

后续更新    支持其他缓存类型...

谢谢