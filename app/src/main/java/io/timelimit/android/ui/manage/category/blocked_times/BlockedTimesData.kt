/*
 * TimeLimit Copyright <C> 2019 - 2023 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.ui.manage.category.blocked_times

import androidx.compose.runtime.Immutable
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.TimeLimitRule
import java.util.BitSet
import kotlin.experimental.or

class BlockedTimesData (val ranges: RangeList) {
    companion object {
        fun fromRules(rules: List<TimeLimitRule>): BlockedTimesData {
            val ranges = mutableListOf<Range>()

            for (rule in rules) {
                if (!rule.likeBlockedTimeArea) continue

                for (day in 0 until 7) {
                    if (1 shl day and rule.dayMask.toInt() == 0) continue

                    ranges.add(
                        Range(
                            day * Category.MINUTES_PER_DAY + rule.startMinuteOfDay,
                            day * Category.MINUTES_PER_DAY + rule.endMinuteOfDay
                        )
                    )
                }
            }

            return BlockedTimesData(RangeList(emptyList()).withRanges(ranges))
        }
    }

    data class Range(val first: Int, val last: Int) {
        init {
            if (last < first) throw IllegalArgumentException()
        }

        fun offsetBy(value: Int) = Range(first + value, last + value)
    }

    @Immutable
    data class RangeList (val ranges: List<Range>) {
        companion object {
            fun fromBitmask(bitmask: ImmutableBitmask): RangeList {
                val result = mutableListOf<Range>()

                var c = 0; while (true) {
                    val firstSetBit = bitmask.dataNotToModify.nextSetBit(c)

                    if (firstSetBit == -1) break

                    c = (bitmask.dataNotToModify.nextClearBit(firstSetBit))

                    result.add(Range(firstSetBit, c - 1))
                }

                return RangeList(result)
            }
        }

        class Reader(rangeList: RangeList, private var cursor: Int = 0) {
            private val iterator = rangeList.ranges.iterator()
            private var currentItem = if (iterator.hasNext()) iterator.next() else null

            fun countSetBits(length: Int): Int {
                var remainingLength = length
                var result = 0

                while (remainingLength > 0) {
                    val cachedCurrentItem = currentItem ?: break

                    if (cursor < cachedCurrentItem.first) {
                        val diff = (cachedCurrentItem.first - cursor).coerceAtMost(remainingLength)

                        cursor += diff
                        remainingLength -= diff
                    } else if (cursor > cachedCurrentItem.last) {
                        if (iterator.hasNext()) currentItem = iterator.next()
                        else currentItem = null
                    } else /* cursor is in the current item */ {
                        val diff = (cachedCurrentItem.last - cursor + 1).coerceAtMost(remainingLength)

                        cursor += diff
                        result += diff
                        remainingLength -= diff
                    }
                }

                return result
            }
        }

        init {
            ranges.zipWithNext { a, b ->
                if (a.last >= b.first) throw IllegalStateException()
            }
        }

        fun readFrom(index: Int) = Reader(this, index)

        fun takePart(range: Range): RangeList = ranges.mapNotNull {
            if (it.last < range.first) null
            else if (it.first > range.last) null
            else if (it.first >= range.first && it.last <= range.last) it
            else Range(
                first = it.first.coerceAtLeast(range.first),
                last = it.last.coerceAtMost(range.last)
            )
        }.let { RangeList(it) }

        fun offsetBy(value: Int): RangeList = RangeList(ranges.map { it.offsetBy(value) })

        fun withoutRange(range: Range): RangeList {
            val result = mutableListOf<Range>()

            for (oldRange in ranges) {
                // simple case one: before the removed range
                if (oldRange.first < range.first && oldRange.last < range.first) result.add(oldRange)
                // simple case two: after the removed range
                else if (oldRange.first > range.last) result.add(oldRange)
                // simple case three: within the deleted range
                else if (oldRange.first >= range.first && oldRange.last <= range.last) {/* nothing to do */}
                // complex case: before and/or after the deleted range
                else {
                    val hasBefore = oldRange.first < range.first
                    val hasAfter = oldRange.last > range.last

                    if (hasBefore) result.add(Range(oldRange.first, range.first - 1))
                    if (hasAfter) result.add(Range(range.last + 1, oldRange.last))
                }
            }

            return RangeList(result)
        }

        fun withRange(range: Range) = withRanges(listOf(range))

        fun withRanges(ranges: RangeList) = withRanges(ranges.ranges)

        internal fun withRanges(ranges: List<Range>): RangeList {
            val result = mutableListOf<Range>()

            for (r in (this.ranges + ranges).sortedBy { it.first }) {
                val previous = result.removeLastOrNull()

                if (previous == null) result.add(r)
                else if (previous.last + 1 < r.first) {
                    result.add(previous)
                    result.add(r)
                } else result.add(Range(previous.first, r.last))
            }

            return RangeList(result)
        }

        fun toBitmask(): ImmutableBitmask {
            val result = BitSet()

            for (range in ranges) {
                result.set(range.first, range.last + 1)
            }

            return ImmutableBitmask(result)
        }
    }

    fun withUpdatedRange(range: Range, value: Boolean) = BlockedTimesData(
        if (value) ranges.withRange(range) else ranges.withoutRange(range)
    )

    fun union(other: BlockedTimesData) = BlockedTimesData(
        ranges.withRanges(other.ranges)
    )

    fun withConfigCopiedToOtherDates(from: Int, to: Set<Int>): BlockedTimesData {
        val sourceConfig = ranges
            .takePart(rangeForDay(from))

        val rangesWithCleanedTargetDays = to.fold(ranges) { ranges, targetDay ->
            ranges.withoutRange(rangeForDay(targetDay))
        }

        val rangesWithWrittenTargetDays = to.fold(rangesWithCleanedTargetDays) { ranges, targetDay ->
            ranges.withRanges(
                sourceConfig.offsetBy(Category.MINUTES_PER_DAY * (targetDay - from))
            )
        }

        return BlockedTimesData(rangesWithWrittenTargetDays)
    }

    fun toRules(categoryId: String): Map<Range, TimeLimitRule> {
        val result = mutableMapOf<Range, TimeLimitRule>()

        for(day in 0 until 7) {
            val dayRanges = ranges.takePart(rangeForDay(day)).offsetBy(-day * Category.MINUTES_PER_DAY).ranges

            for (range in dayRanges) {
                val rule = result[range] ?: TimeLimitRule(
                    id = IdGenerator.generateId(),
                    categoryId = categoryId,
                    applyToExtraTimeUsage = true,
                    dayMask = 0,
                    maximumTimeInMillis = 0,
                    startMinuteOfDay = range.first,
                    endMinuteOfDay = range.last,
                    sessionDurationMilliseconds = 0,
                    sessionPauseMilliseconds = 0,
                    perDay = false
                )

                result[range] = rule.copy(dayMask = rule.dayMask or (1 shl day).toByte())
            }
        }

        return result
    }

    private fun rangeForDay(day: Int) = Range(Category.MINUTES_PER_DAY * day, Category.MINUTES_PER_DAY * (day + 1) - 1)
}