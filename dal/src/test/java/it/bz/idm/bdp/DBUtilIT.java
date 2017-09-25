package it.bz.idm.bdp;

import static org.junit.Assert.assertNotNull;

import javax.persistence.EntityManager;

import org.junit.Test;

import it.bz.idm.bdp.dal.util.JPAUtil;

public class DBUtilIT {
	
	@Test
	public void testJPAUtilInit() {
		EntityManager entityManager = JPAUtil.createEntityManager();
		assertNotNull(entityManager);
		
	}

}
