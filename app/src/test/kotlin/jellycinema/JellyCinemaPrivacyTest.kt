package org.jellyfin.androidtv.jellycinema

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserDto
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/**
 * The automatic privacy check: nobody may see, in any Jelly Cinema row or the banner, a title they
 * couldn't open in Jellyfin itself (rating limit or library access).
 *
 * Runs against a throwaway server made by ci/privacy/seed.py (see .github/workflows/jellycinema-ci.yaml):
 *   JC_PRIVACY_ACCOUNTS=<media dir>/accounts.json ./gradlew :app:testDebugUnitTest --tests '*JellyCinemaPrivacyTest*'
 * Skipped when JC_PRIVACY_ACCOUNTS isn't set (normal local builds).
 */
class JellyCinemaPrivacyTest : FunSpec({
	val accountsFile = System.getenv("JC_PRIVACY_ACCOUNTS")?.let(::File)

	test("no Jelly Cinema row or banner shows a restricted account anything it may not open").config(enabled = accountsFile?.exists() == true) {
		val setup = Json.parseToJsonElement(accountsFile!!.readText()).jsonObject
		val server = setup["server"]!!.jsonPrimitive.content
		val passwords = setup["accounts"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
		val restricted = setup["restricted"]!!.jsonArray.map { it.jsonPrimitive.content }

		val jellyfin = createJellyfin {
			context = mockk(relaxed = true)
			clientInfo = ClientInfo("Jelly Cinema privacy check", "1")
			deviceInfo = DeviceInfo("jc-privacy-check", "CI")
		}
		suspend fun signIn(name: String): Pair<ApiClient, UserDto> {
			val api = jellyfin.createApi(baseUrl = server)
			val result = api.userApi.authenticateUserByName(name, passwords.getValue(name)).content
			api.update(accessToken = result.accessToken)
			return api to result.user!!
		}

		// the admin's Top 10 ranking (built from everyone's watch history, R-rated movies included)
		val (adminApi, admin) = signIn("admin")
		val adminRepo = JellyCinemaRepository(adminApi)
		adminRepo.ensureConfig()
		adminRepo.refreshRankingIfNeeded(admin, force = true)
		val adminTop = adminRepo.top10(admin.id).first
		withClue("the test only means something if the shared Top 10 holds R-rated movies") {
			adminTop.any { it.officialRating == "R" } shouldBe true
		}

		// rating values as Jellyfin compares them
		val ratingValue = adminApi.localizationApi.getParentalRatings().content
			.mapNotNull { r -> r.value?.let { r.name to it } }.toMap()

		// the dates every seasonal row is on, plus a Saturday for Picks
		val year = LocalDate.now().year
		val seasonDays = listOf(LocalDate.of(year, 10, 20), LocalDate.of(year, 12, 10), LocalDate.of(year, 4, 1), LocalDate.of(year, 4, 15))
		val saturday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

		for (name in restricted) {
			val (api, user) = signIn(name)
			val policy = adminApi.userApi.getUserById(user.id).content.policy!!
			val repo = JellyCinemaRepository(api)
			repo.ensureConfig()

			// everything the app would put on this person's Home
			val shown = mutableMapOf<String, List<BaseItemDto>>()
			shown["banner"] = repo.heroItems()
			val (topMovies, topShows) = repo.top10(user.id)
			shown["Top 10 movies"] = topMovies
			shown["Top 10 shows"] = topShows
			for (day in seasonDays) repo.seasonal(day).forEach { (row, items) -> shown["${row.key} on $day"] = items }
			shown["Picks"] = repo.picks(saturday)
			repo.becauseYouWatched(4).forEach { (t, items) -> shown["because $t"] = items }
			repo.genreRows(8).forEach { (t, items) -> shown["genre $t"] = items }
			if (policy.maxParentalRating == null) shown["Collections"] = repo.collections()
			shown["Watch Again"] = repo.watchAgain()

			withClue("$name should get some rows, or the check proves nothing") {
				shown.values.flatten().shouldNotBeEmpty()
			}

			val leaks = mutableListOf<String>()
			for ((row, items) in shown) for (item in items) {
				// 1. Jellyfin itself lets this person open it
				val opens = runCatching { api.userLibraryApi.getItem(itemId = item.id).content }.isSuccess
				if (!opens) leaks += "$row: '${item.name}' can't be opened by $name"
				// 2. within the rating limit (checked independently, as the admin)
				val max = policy.maxParentalRating
				val full = adminApi.userLibraryApi.getItem(itemId = item.id).content
				if (max != null && full.type != BaseItemKind.BOX_SET) {
					val value = full.officialRating?.let { ratingValue[it] }
					if (value == null || value > max) leaks += "$row: '${item.name}' (${full.officialRating}) is above $name's limit"
				}
				// 3. in a library this person may use (checked independently, as the admin)
				if (policy.enableAllFolders != true) {
					val ancestors = adminApi.libraryApi.getAncestors(itemId = item.id).content.map { it.id.toString().replace("-", "") }
					val allowed = policy.enabledFolders.orEmpty().map { it.toString().replace("-", "") }
					if (ancestors.none { it in allowed }) leaks += "$row: '${item.name}' is outside $name's libraries"
				}
			}
			withClue(leaks.joinToString("\n")) { leaks.size shouldBe 0 }
			println("privacy check: $name saw ${shown.values.sumOf { it.size }} titles in ${shown.count { it.value.isNotEmpty() }} rows, nothing they shouldn't")
		}
	}
})
