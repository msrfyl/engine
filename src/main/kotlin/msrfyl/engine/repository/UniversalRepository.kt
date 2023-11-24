package msrfyl.engine.repository

import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.PagingAndSortingRepository

@NoRepositoryBean
interface UniversalRepository<T, ID> : PagingAndSortingRepository<T, ID>,
    JpaSpecificationExecutor<T>, CrudRepository<T, ID>