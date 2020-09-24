-- Comune Di Bolzano
(
	s.stationtype = 'Mobilestation'
	or (s.stationtype = 'TrafficSensor' and s.origin in ('FAMAS-traffic'))
)
