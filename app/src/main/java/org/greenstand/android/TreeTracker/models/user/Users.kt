package org.greenstand.android.TreeTracker.models

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.greenstand.android.TreeTracker.analytics.Analytics
import org.greenstand.android.TreeTracker.database.TreeTrackerDAO
import org.greenstand.android.TreeTracker.database.entity.PlanterCheckInEntity
import org.greenstand.android.TreeTracker.database.entity.PlanterInfoEntity
import org.greenstand.android.TreeTracker.models.user.User

class Users(
    private val locationUpdateManager: LocationUpdateManager,
    private val dao: TreeTrackerDAO,
    private val analytics: Analytics
) {

    var currentSessionUser: User? = null
        private set

    fun users(): Flow<List<User>> {
        return dao.getAllPlanterInfo()
            .map { planters -> planters.mapNotNull { createUser(it) } }
    }

    suspend fun getUserList(): List<User> {
        return dao.getAllPlanterInfoList()
            .mapNotNull { planter -> createUser(planter) }
    }

    suspend fun getUser(planterInfoId: Long): User? {
        return createUser(
            dao.getPlanterInfoById(planterInfoId))
    }

    suspend fun getUserWithIdentifier(identifier: String): User? {
        return createUser(
            dao.getPlanterInfoByIdentifier(identifier)
        )
    }

    suspend fun getPowerUser(): User? {
        val planterInfo = dao.getPowerUser() ?: return null
        return createUser(planterInfo)
    }

    suspend fun createUser(
        firstName: String,
        lastName: String,
        organization: String?,
        phone: String?,
        email: String?,
        identifier: String,
        photoPath: String,
        isPowerUser: Boolean = false
    ): Long {
        return withContext(Dispatchers.IO) {

            val location = locationUpdateManager.currentLocation
            val time = location?.time ?: System.currentTimeMillis()

            val entity = PlanterInfoEntity(
                identifier = identifier,
                firstName = firstName,
                lastName = lastName,
                organization = organization,
                phone = phone,
                email = email,
                longitude = location?.longitude ?: 0.0,
                latitude = location?.latitude ?: 0.0,
                createdAt = time,
                uploaded = false,
                recordUuid = UUID.randomUUID().toString(),
                isPowerUser = isPowerUser,
                localPhotoPath = photoPath,
            )

            dao.insertPlanterInfo(entity).also {
                analytics.userInfoCreated(
                    phone = phone.orEmpty(),
                    email = email.orEmpty()
                )
            }
        }
    }

    suspend fun doesUserExists(identifier: String): Boolean{
        return getUserWithIdentifier(identifier) != null
    }

    private suspend fun createUser(planterInfoEntity: PlanterInfoEntity?): User? {
        planterInfoEntity ?: return null
        return User(
            id = planterInfoEntity.id,
            wallet = planterInfoEntity.identifier,
            firstName = planterInfoEntity.firstName,
            lastName = planterInfoEntity.lastName,
            photoPath = planterInfoEntity.localPhotoPath,
            isPowerUser = planterInfoEntity.isPowerUser,
            numberOfTrees = dao.getTreesByEachPlanter(planterInfoEntity.identifier).toString()
        )
    }
}
