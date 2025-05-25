package fr.alainco.jhttpp2.core.internal;
/* Written and copyright 2001-2011 Benjamin Kohl.
 * Distributed under the GNU General Public License; see the README file.
 * This code comes with NO WARRANTY.
 */

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class Jhttpp2ServerInputStream extends BufferedInputStream implements Jhttpp2InputStream
{
	private Jhttpp2HTTPSession connection;

	public Jhttpp2ServerInputStream(Jhttpp2Server server,
			Jhttpp2HTTPSession connection, InputStream a, boolean filter) {
		super(a,server.http_proxy_response_buffer_size);
		this.connection = connection;
	}

	public int read_f(byte[] b) throws IOException {
		return read(b);
	}
}

