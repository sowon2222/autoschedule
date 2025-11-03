import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'

// CSS is loaded via CDN links in index.html


export default function Calendar() {
  return (
    <div className="p-2">
      <FullCalendar plugins={[dayGridPlugin, timeGridPlugin]} initialView="timeGridWeek" height="auto" />
    </div>
  )
}


