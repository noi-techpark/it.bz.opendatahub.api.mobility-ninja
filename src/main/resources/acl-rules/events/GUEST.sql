-- OPEN DATA WHERE CLAUSE FOR THE GUEST ACCOUNT
-- This is the default role, which will be concatenated with a logical OR operator
-- to all other rules.
-- It defines OPEN DATA RULES seen by all guests, that is, people that are not logged in
(
	ev.origin = 'PROVINCE_BZ'
)
