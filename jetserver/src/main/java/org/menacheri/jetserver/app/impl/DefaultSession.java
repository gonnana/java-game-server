package org.menacheri.jetserver.app.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.netty.channel.Channel;
import org.menacheri.jetserver.app.Session;
import org.menacheri.jetserver.communication.MessageSender.Fast;
import org.menacheri.jetserver.communication.MessageSender.Reliable;
import org.menacheri.jetserver.event.Events;
import org.menacheri.jetserver.event.Event;
import org.menacheri.jetserver.event.EventDispatcher;
import org.menacheri.jetserver.event.EventHandler;
import org.menacheri.jetserver.event.impl.EventDispatchers;


/**
 * The default implementation of the session class. This class is responsible
 * for receiving and sending events. For receiving it uses the
 * {@link #onEvent(Event)} method and for sending it uses the
 * {@link EventDispatcher} fireEvent method. The Method {@link #setId(Object)}
 * will throw {@link IllegalArgumentException} in this implementation class.
 * 
 * @author Abraham Menacherry
 * 
 */
public class DefaultSession implements Session
{
	/**
	 * session id
	 */
	protected final String id;
	/**
	 * event dispatcher
	 */
	protected final EventDispatcher eventDispatcher;

	/**
	 * session parameters
	 */
	protected final Map<String, Object> sessionAttributes;

	protected final long creationTime;

	protected long lastReadWriteTime;

	protected Status status;

	protected boolean isWriteable;

	/**
	 * Life cycle variable to check if the session is shutting down. If it is, then no
	 * more incoming events will be accepted.
	 */
	protected volatile boolean isShuttingDown;
	
	protected boolean isUDPEnabled;
	
	protected final Map<String, Object> connectParameters;

	protected Reliable tcpSender = null;
	
	protected Fast udpSender = null;
	
	protected DefaultSession(SessionBuilder sessionBuilder)
	{
		// validate variables and provide default values if necessary. Normally
		// done in the builder.build() method, but done here since this class is
		// meant to be overriden and this could be easier.
		sessionBuilder.validateAndSetValues();
		this.id = sessionBuilder.id;
		this.eventDispatcher = sessionBuilder.eventDispatcher;
		this.sessionAttributes = sessionBuilder.sessionAttributes;
		this.creationTime = sessionBuilder.creationTime;
		this.status = sessionBuilder.status;
		this.lastReadWriteTime = sessionBuilder.lastReadWriteTime;
		this.isWriteable = sessionBuilder.isWriteable;
		this.isShuttingDown = sessionBuilder.isShuttingDown;
		this.isUDPEnabled = sessionBuilder.isUDPEnabled;
		this.connectParameters = sessionBuilder.connectParameters;
	}
	
	/**
	 * This class is roughly based on Joshua Bloch's Builder pattern. Since
	 * Session class will be extended by child classes, the
	 * {@link #validateAndSetValues()} method on this builder is actually called
	 * by the {@link DefaultSession} constructor for ease of use. May not be good
	 * design though.
	 * 
	 * @author Abraham, Menacherry
	 * 
	 */
	public static class SessionBuilder
	{
		private String id = null;
		private EventDispatcher eventDispatcher = null;
		private Map<String, Object> sessionAttributes = null;
		private long creationTime = 0l;
		private long lastReadWriteTime = 0l;
		private Status status = Status.NOT_CONNECTED;
		private boolean isWriteable = true;
		private volatile boolean isShuttingDown = false;
		private boolean isUDPEnabled = false;// By default UDP is not enabled.
		private Map<String, Object> connectParameters = null;
		
		public Session build()
		{
			return new DefaultSession(this);
		}
		
		/**
		 * This method is used to validate and set the variables to default
		 * values if they are not already set before calling build. This method
		 * is invoked by the constructor of SessionBuilder. <b>Important!</b>
		 * Builder child classes which override this method need to call
		 * super.validateAndSetValues(), otherwise you could get runtime NPE's.
		 */
		protected void validateAndSetValues(){
			if (null == eventDispatcher)
			{
				eventDispatcher = EventDispatchers.newJetlangEventDispatcher();
			}
			if(null == sessionAttributes)
			{
				sessionAttributes = new HashMap<String, Object>();
			}
			if(null == connectParameters)
			{
				connectParameters = new HashMap<String, Object>();
			}
			creationTime = System.currentTimeMillis();
		}
		
		public String getId()
		{
			return id;
		}
		public SessionBuilder id(final String id)
		{
			this.id = id;
			return this;
		}
		public SessionBuilder eventDispatcher(final EventDispatcher eventDispatcher)
		{
			this.eventDispatcher = eventDispatcher;
			return this;
		}
		public SessionBuilder sessionAttributes(final Map<String, Object> sessionAttributes)
		{
			this.sessionAttributes = sessionAttributes;
			return this;
		}
		public SessionBuilder creationTime(long creationTime)
		{
			this.creationTime = creationTime;
			return this;
		}
		public SessionBuilder lastReadWriteTime(long lastReadWriteTime)
		{
			this.lastReadWriteTime = lastReadWriteTime;
			return this;
		}
		public SessionBuilder status(Status status)
		{
			this.status = status;
			return this;
		}
		public SessionBuilder isWriteable(boolean isWriteable)
		{
			this.isWriteable = isWriteable;
			return this;
		}
		public SessionBuilder isShuttingDown(boolean isShuttingDown)
		{
			this.isShuttingDown = isShuttingDown;
			return this;
		}
		public SessionBuilder isUDPEnabled(boolean isUDPEnabled)
		{
			this.isUDPEnabled = isUDPEnabled;
			return this;
		}
		public SessionBuilder connectParameters(Map<String, Object> connectParameters)
		{
			this.connectParameters = connectParameters;
			return this;
		}
		
	}
	
	@Override
	public void onEvent(Event event)
	{
		if(!isShuttingDown){
			eventDispatcher.fireEvent(event);
		}
	}

	@Override
	public Object getId()
	{
		return id;
	}

	@Override
	public void setId(Object id)
	{
		throw new IllegalArgumentException("id cannot be set in this implementation, since it is final");
	}

	@Override
	public EventDispatcher getEventDispatcher()
	{
		return eventDispatcher;
	}

	@Override
	public void addHandler(EventHandler eventHandler)
	{
		eventDispatcher.addHandler(eventHandler);
	}

	@Override
	public void removeHandler(EventHandler eventHandler)
	{
		eventDispatcher.removeHandler(eventHandler);
	}
	
	@Override
	public List<EventHandler> getEventHandlers(int eventType)
	{
		return eventDispatcher.getHandlers(eventType);
	}

	@Override
	public Object getAttribute(String key)
	{
		return sessionAttributes.get(key);
	}

	@Override
	public void removeAttribute(String key)
	{
		sessionAttributes.remove(key);
		Event event = Events.changeAttributeEvent(key, null);
		eventDispatcher.fireEvent(event);
	}

	@Override
	public void setAttribute(String key, Object value)
	{
		sessionAttributes.put(key, value);
		Event event = Events.changeAttributeEvent(key, value);
		eventDispatcher.fireEvent(event);
	}

	/**
	 * Get connect parameter
	 * 
	 * @param key
	 *            Find out a connect parameter for the this key.
	 * @return connect parameter
	 */
	public Object getConnectParameter(String key) {
		return this.connectParameters.get(key);
	}

	/**
	 * Set connect parameter
	 * 
	 * @param key
	 *            The key to be set. It should be a string.
	 * @param object
	 *            . The connection object to be set. If using a Netty
	 *            implementation, it would be {@link Channel}
	 */
	public void setConnectParameter(String key, Object object) {
		this.connectParameters.put(key, object);
	}

	/**
	 * Remove connect parameter
	 * 
	 * @param key
	 *            The connect parameter to be removed based on the key.
	 */
	public void removeConnectParameter(String key)
	{
		this.connectParameters.remove(key);
	}

	@Override
	public long getCreationTime()
	{
		return creationTime;
	}

	@Override
	public long getLastReadWriteTime()
	{
		return lastReadWriteTime;
	}

	public void setLastReadWriteTime(long lastReadWriteTime)
	{
		this.lastReadWriteTime = lastReadWriteTime;
	}

	@Override
	public Status getStatus()
	{
		return status;
	}

	@Override
	public void setStatus(Status status)
	{
		this.status = status;

	}

	@Override
	public boolean isConnected()
	{
		return this.status == Status.CONNECTED;
	}

	@Override
	public boolean isWriteable()
	{
		return isWriteable;
	}

	@Override
	public void setWriteable(boolean isWriteable)
	{
		this.isWriteable = isWriteable;
	}

	/**
	 * Not synchronized because default implementation does not care whether a
	 * duplicated message sender is created.
	 * 
	 * @see org.menacheri.jetserver.app.Session#isUDPEnabled()
	 */
	@Override
	public boolean isUDPEnabled()
	{
		return isUDPEnabled;
	}
	
	@Override
	public void setUDPEnabled(boolean isEnabled)
	{
		this.isUDPEnabled = isEnabled;
	}
	
	@Override
	public synchronized void close()
	{
		isShuttingDown = true;
		eventDispatcher.close();
		if(null != tcpSender){
			tcpSender.close();
			tcpSender = null;
		}
		if(null != udpSender){
			udpSender.close();
			udpSender = null;
		}
		this.status = Status.CLOSED;
	}
	
	@Override
	public boolean isShuttingDown()
	{
		return isShuttingDown;
	}

	public Map<String, Object> getSessionAttributes()
	{
		return sessionAttributes;
	}

	public Map<String, Object> getConnectParameters()
	{
		return connectParameters;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DefaultPlayerSession other = (DefaultPlayerSession) obj;
		if (id == null)
		{
			if (other.id != null)
				return false;
		}
		else if (!id.equals(other.id))
			return false;
		return true;
	}

	@Override
	public Reliable getTcpSender()
	{
		return tcpSender;
	}

	@Override
	public void setTcpSender(Reliable tcpSender)
	{
		this.tcpSender = tcpSender;
	}

	@Override
	public Fast getUdpSender()
	{
		return udpSender;
	}

	@Override
	public void setUdpSender(Fast udpSender)
	{
		this.udpSender = udpSender;
	}
}
